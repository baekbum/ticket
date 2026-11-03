package dev.bum.ticket_service.service.event;

import dev.bum.common.service.ticket.area.enums.AreaStatus;
import dev.bum.common.service.ticket.event.event.dto.DeleteEventBulkRequest;
import dev.bum.common.service.ticket.event.event.dto.InsertEventBulkRequest;
import dev.bum.common.service.ticket.event.event.dto.InsertEventCommonRequest;
import dev.bum.common.service.ticket.event.event.dto.InsertEventScheduleRequest;
import dev.bum.common.service.ticket.event.event.dto.UpdateEventRequest;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.ticket_service.config.QuerydslConfig;
import dev.bum.ticket_service.jpa.area.Area;
import dev.bum.ticket_service.jpa.area.AreaJpaRepository;
import dev.bum.ticket_service.jpa.event.event.EventJpaRepository;
import dev.bum.ticket_service.jpa.event.event.EventRepositoryImpl;
import dev.bum.ticket_service.jpa.event.policy.EventPolicyOutbox;
import dev.bum.ticket_service.jpa.event.policy.EventPolicyOutboxJpaRepository;
import dev.bum.ticket_service.service.event.event.EventManagementService;
import dev.bum.ticket_service.service.event.file.EventPosterService;
import dev.bum.ticket_service.service.event.policy.EventPolicyOutboxService;
import dev.bum.common.service.ticket.event.event.dto.EventResponse;
import dev.bum.ticket_service.jpa.event.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
@Import({EventManagementService.class, EventRepositoryImpl.class, QuerydslConfig.class, EventPolicyOutboxService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EventManagementPolicyIntegrationTest {
    @Autowired EventManagementService service;
    @Autowired EventJpaRepository events;
    @Autowired EventPolicyOutboxJpaRepository outbox;
    @Autowired AreaJpaRepository areas;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean EventPosterService posters;

    @BeforeEach
    void cleanDatabase() {
        areas.deleteAll();
        outbox.deleteAll();
        events.deleteAll();
    }

    @Test
    void bulk_insert_commits_one_policy_snapshot_per_schedule() {
        List<EventResponse> responses = service.insertBulk(bulkRequest(), null);

        assertThat(events.count()).isEqualTo(2);
        assertThat(outbox.count()).isEqualTo(2);
        for (EventResponse response : responses) {
            Event event = events.findById(response.getEventId()).orElseThrow();
            assertThat(policies(event.getEventId())).singleElement().satisfies(policy -> {
                assertThat(policy.getStatus()).isEqualTo("ON_SALE");
                assertThat(policy.getAdmissionSize()).isEqualTo(event.getQueueAdmissionSize());
                assertThat(policy.getSaleStartAt()).isEqualTo(epoch(event.getSaleStartAt()));
                assertThat(policy.getSaleEndAt()).isEqualTo(epoch(event.getSaleEndAt()));
                assertThat(policy.getEventDateTime()).isEqualTo(epoch(event.getEventDateTime()));
                assertThat(policy.isPublished()).isFalse();
            });
        }
    }

    @Test
    void bulk_insert_defaults_missing_size_and_publishes_each_schedule_size() {
        InsertEventBulkRequest request = bulkRequest();
        request.getSchedules().getFirst().setQueueAdmissionSize(null);
        request.getSchedules().getLast().setQueueAdmissionSize(250);

        List<EventResponse> responses = service.insertBulk(request, null);

        assertThat(responses).extracting(EventResponse::getQueueAdmissionSize).containsExactly(1000, 250);
        for (EventResponse response : responses) {
            assertThat(service.selectById(response.getEventId()).getQueueAdmissionSize())
                    .isEqualTo(response.getQueueAdmissionSize());
            assertThat(policies(response.getEventId())).singleElement().satisfies(policy ->
                    assertThat(policy.getAdmissionSize()).isEqualTo(response.getQueueAdmissionSize()));
        }
    }

    @Test
    void both_update_paths_and_bulk_delete_commit_ordered_policy_snapshots() {
        List<Long> ids = service.insertBulk(bulkRequest(), null).stream().map(response -> response.getEventId()).toList();
        Long first = ids.getFirst();
        service.update(first, UpdateEventRequest.builder().queueAdmissionSize(7).build());
        service.update(first, UpdateEventRequest.builder().queueAdmissionSize(9).build(), null);
        service.deleteBulk(DeleteEventBulkRequest.builder().eventIds(ids).build());

        assertThat(events.count()).isZero();
        assertThat(outbox.count()).isEqualTo(6);
        List<EventPolicyOutbox> firstPolicies = policies(first);
        assertThat(firstPolicies).extracting(EventPolicyOutbox::getStatus)
                .containsExactly("ON_SALE", "ON_SALE", "ON_SALE", "DELETED");
        assertThat(firstPolicies).extracting(EventPolicyOutbox::getAdmissionSize).containsExactly(100, 7, 9, 9);
        for (int index = 1; index < firstPolicies.size(); index++) {
            assertThat(firstPolicies.get(index).getOutboxId()).isGreaterThan(firstPolicies.get(index - 1).getOutboxId());
        }
        assertThat(policies(ids.getLast())).extracting(EventPolicyOutbox::getStatus)
                .containsExactly("ON_SALE", "DELETED");
    }

    @Test
    void outer_rollback_reverts_event_update_and_the_new_policy_snapshot() {
        Long id = service.insertBulk(bulkRequest(), null).getFirst().getEventId();
        List<Long> originalPolicyIds = outbox.findAll().stream().map(EventPolicyOutbox::getOutboxId).toList();

        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            service.update(id, UpdateEventRequest.builder().status(EventStatus.CANCELLED).build());
            assertThat(outbox.count()).isEqualTo(3);
            throw new IllegalStateException("후속 작업 실패");
        })).isInstanceOf(IllegalStateException.class).hasMessage("후속 작업 실패");

        assertThat(events.findById(id).orElseThrow().getStatus()).isEqualTo(EventStatus.ON_SALE);
        assertThat(outbox.findAll()).extracting(EventPolicyOutbox::getOutboxId)
                .containsExactlyInAnyOrderElementsOf(originalPolicyIds);
    }

    @Test
    void failed_bulk_delete_restores_earlier_deletions_and_discards_all_delete_snapshots() {
        List<Long> ids = service.insertBulk(bulkRequest(), null).stream().map(response -> response.getEventId()).toList();
        areas.saveAndFlush(Area.builder().event(events.findById(ids.getLast()).orElseThrow())
                .areaName("A").layoutKey("A").grade(SeatGrade.VIP).price(10000).status(AreaStatus.ACTIVE).build());

        assertThatThrownBy(() -> service.deleteBulk(DeleteEventBulkRequest.builder().eventIds(ids).build()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(events.findAllById(ids)).hasSize(2);
        assertThat(areas.count()).isEqualTo(1);
        assertThat(outbox.findAll()).hasSize(2).allSatisfy(policy ->
                assertThat(policy.getStatus()).isEqualTo("ON_SALE"));
    }

    private List<EventPolicyOutbox> policies(Long eventId) {
        return outbox.findAll().stream().filter(policy -> policy.getEventId().equals(eventId))
                .sorted(Comparator.comparing(EventPolicyOutbox::getOutboxId)).toList();
    }

    private InsertEventBulkRequest bulkRequest() {
        LocalDateTime first = LocalDateTime.of(2027, 1, 1, 18, 0);
        return InsertEventBulkRequest.builder()
                .common(InsertEventCommonRequest.builder().artistName("IU").title("정책 테스트 공연")
                        .eventGroupCode("POLICY_TEST").venue("공연장").totalSeats(100).maxTicketsPerPerson(4).build())
                .schedules(List.of(schedule(first, 100), schedule(first.plusDays(1), 200))).build();
    }

    private InsertEventScheduleRequest schedule(LocalDateTime time, int admissionSize) {
        return InsertEventScheduleRequest.builder().eventDateTime(time).saleStartAt(time.minusMonths(1))
                .saleEndAt(time.minusDays(1)).cancelDeadlineAt(time.minusDays(1))
                .queueAdmissionSize(admissionSize).build();
    }

    private long epoch(LocalDateTime time) {
        return time.atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli();
    }
}
