package dev.bum.ticket_service.jpa.event;

import dev.bum.common.service.ticket.event.event.dto.EventCondRequest;
import dev.bum.common.service.ticket.event.event.dto.InsertEventRequest;
import dev.bum.common.service.ticket.event.event.dto.UpdateEventRequest;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.ticket_service.config.QuerydslConfig;
import dev.bum.ticket_service.exception.event.EventDuplicateException;
import dev.bum.ticket_service.exception.event.EventNotExistException;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventJpaRepository;
import dev.bum.ticket_service.jpa.event.event.EventRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@Import({EventRepositoryImpl.class, QuerydslConfig.class})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class EventRepositoryImplTest {

    @Autowired
    private EventRepositoryImpl eventRepository;

    @Autowired
    private EventJpaRepository jpaRepository;

    @BeforeEach
    void setUp() {
        jpaRepository.save(event("IU", "IU Concert", "KSPO Dome", LocalDateTime.of(2026, 9, 18, 18, 0)));
        jpaRepository.save(event("AKMU", "AKMU Concert", "Olympic Hall", LocalDateTime.of(2026, 10, 1, 18, 0)));
    }

    @Test
    @DisplayName("이벤트 등록")
    void event_insert() {
        InsertEventRequest info = insertRequest("BTS", "BTS Concert", "Main Stadium", LocalDateTime.of(2026, 11, 1, 19, 0));

        Event response = eventRepository.insert(info);

        assertThat(response.getEventId()).isNotNull();
        assertThat(response.getArtistName()).isEqualTo("BTS");
        assertThat(response.getTitle()).isEqualTo("BTS Concert");
        assertThat(response.getVenue()).isEqualTo("Main Stadium");
        assertThat(response.getAvailableSeats()).isEqualTo(30000);
        assertThat(response.getStatus()).isEqualTo(EventStatus.ON_SALE);
    }

    @Test
    @DisplayName("동일한 이벤트 등록 시 예외 발생")
    void event_insert_duplicate() {
        InsertEventRequest info = insertRequest("IU", "IU Concert", "KSPO Dome", LocalDateTime.of(2026, 9, 18, 18, 0));

        assertThatThrownBy(() -> eventRepository.insert(info))
                .isInstanceOf(EventDuplicateException.class);
    }

    @Test
    @DisplayName("중복 이벤트가 없으면 중복 체크 통과")
    void is_exist_success() {
        EventCondRequest cond = EventCondRequest.builder()
                .artistName("BTS")
                .title("BTS Concert")
                .venue("Main Stadium")
                .eventDate(LocalDate.of(2026, 11, 1))
                .status(EventStatus.ON_SALE)
                .build();

        eventRepository.isExist(cond);
    }

    @Test
    @DisplayName("ID로 이벤트 조회")
    void event_select_by_id() {
        Event saved = jpaRepository.save(event("BTS", "BTS Concert", "Main Stadium", LocalDateTime.of(2026, 11, 1, 19, 0)));

        Event response = eventRepository.selectById(saved.getEventId());

        assertThat(response.getEventId()).isEqualTo(saved.getEventId());
        assertThat(response.getTitle()).isEqualTo("BTS Concert");
    }

    @Test
    @DisplayName("존재하지 않는 ID 조회 시 예외 발생")
    void event_select_by_id_fail() {
        assertThatThrownBy(() -> eventRepository.selectById(999L))
                .isInstanceOf(EventNotExistException.class);
    }

    @Test
    @DisplayName("조건으로 이벤트 조회")
    void event_select_by_cond() {
        EventCondRequest cond = EventCondRequest.builder()
                .artistName("IU")
                .eventDate(LocalDate.of(2026, 9, 18))
                .status(EventStatus.ON_SALE)
                .build();

        Page<Event> response = eventRepository.selectByCond(cond, PageRequest.of(0, 10));

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().get(0).getArtistName()).isEqualTo("IU");
    }

    @Test
    @DisplayName("기간 조건으로 이벤트 조회")
    void event_select_by_date_range() {
        EventCondRequest cond = EventCondRequest.builder()
                .eventDateFrom(LocalDate.of(2026, 9, 1))
                .eventDateTo(LocalDate.of(2026, 9, 30))
                .build();

        Page<Event> response = eventRepository.selectByCond(cond, PageRequest.of(0, 10));

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().get(0).getTitle()).isEqualTo("IU Concert");
    }

    @Test
    @DisplayName("정렬 조건으로 이벤트 조회")
    void event_select_by_cond_with_sort() {
        EventCondRequest cond = EventCondRequest.builder().build();

        Page<Event> response = eventRepository.selectByCond(
                cond,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "eventDateTime"))
        );

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).getTitle()).isEqualTo("AKMU Concert");
        assertThat(response.getContent().get(1).getTitle()).isEqualTo("IU Concert");
    }

    @Test
    @DisplayName("이벤트 정보 수정")
    void event_update() {
        Event saved = jpaRepository.save(event("BTS", "BTS Concert", "Main Stadium", LocalDateTime.of(2026, 11, 1, 19, 0)));
        UpdateEventRequest info = UpdateEventRequest.builder()
                .title("Updated BTS Concert")
                .venue("Updated Stadium")
                .totalSeats(35000)
                .availableSeats(34000)
                .status(EventStatus.SOLD_OUT)
                .build();

        Event response = eventRepository.update(saved.getEventId(), info);

        assertThat(response.getTitle()).isEqualTo("Updated BTS Concert");
        assertThat(response.getVenue()).isEqualTo("Updated Stadium");
        assertThat(response.getTotalSeats()).isEqualTo(35000);
        assertThat(response.getAvailableSeats()).isEqualTo(34000);
        assertThat(response.getStatus()).isEqualTo(EventStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("이벤트 삭제")
    void event_delete() {
        Event saved = jpaRepository.save(event("BTS", "BTS Concert", "Main Stadium", LocalDateTime.of(2026, 11, 1, 19, 0)));

        Event deleted = eventRepository.delete(saved.getEventId());

        assertThat(deleted.getEventId()).isEqualTo(saved.getEventId());
        assertThatThrownBy(() -> eventRepository.selectById(saved.getEventId()))
                .isInstanceOf(EventNotExistException.class);
    }

    private Event event(String artistName, String title, String venue, LocalDateTime eventDateTime) {
        return Event.builder()
                .artistName(artistName)
                .title(title)
                .description(title + " description")
                .venue(venue)
                .venueAddress("Seoul")
                .eventDateTime(eventDateTime)
                .saleStartAt(eventDateTime.minusMonths(1))
                .saleEndAt(eventDateTime.minusDays(1))
                .cancelDeadlineAt(eventDateTime.minusDays(1))
                .runningMinutes(120)
                .ageLimit(12)
                .totalSeats(30000)
                .availableSeats(30000)
                .status(EventStatus.ON_SALE)
                .maxTicketsPerPerson(4)
                .build();
    }

    private InsertEventRequest insertRequest(String artistName, String title, String venue, LocalDateTime eventDateTime) {
        return InsertEventRequest.builder()
                .artistName(artistName)
                .title(title)
                .description(title + " description")
                .venue(venue)
                .venueAddress("Seoul")
                .eventDateTime(eventDateTime)
                .saleStartAt(eventDateTime.minusMonths(1))
                .saleEndAt(eventDateTime.minusDays(1))
                .cancelDeadlineAt(eventDateTime.minusDays(1))
                .runningMinutes(120)
                .ageLimit(12)
                .totalSeats(30000)
                .maxTicketsPerPerson(4)
                .build();
    }
}
