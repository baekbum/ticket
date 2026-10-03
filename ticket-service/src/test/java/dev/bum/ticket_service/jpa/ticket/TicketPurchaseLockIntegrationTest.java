package dev.bum.ticket_service.jpa.ticket;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.event.event.enums.TicketLimitScope;
import dev.bum.common.service.ticket.reservation.dto.InsertReservationRequest;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.config.QuerydslConfig;
import dev.bum.ticket_service.service.ticket.TicketPurchaseCountService;
import dev.bum.ticket_service.exception.ticket.TicketLimitExceededException;
import dev.bum.ticket_service.jpa.area.AreaRepositoryImpl;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventJpaRepository;
import dev.bum.ticket_service.jpa.event.event.EventRepositoryImpl;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepositoryImpl;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatJpaRepository;
import dev.bum.ticket_service.jpa.seat.SeatRepositoryImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ReservationRepositoryImpl.class, TicketRepositoryImpl.class, SeatRepositoryImpl.class,
        AreaRepositoryImpl.class, EventRepositoryImpl.class, QuerydslConfig.class, TicketPurchaseCountService.class})
class TicketPurchaseLockIntegrationTest {

    @Autowired private ReservationRepositoryImpl reservations;
    @Autowired private TicketPurchaseLockJpaRepository locks;
    @Autowired private EventJpaRepository events;
    @Autowired private SeatJpaRepository seats;
    @Autowired private TicketJpaRepository tickets;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TicketPurchaseCountService counts;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        // 포트가 지정되면 전용 PostgreSQL 테스트 DB만 사용한다. 스키마는 테스트 종료 시 삭제된다.
        String port = System.getenv("TICKET_TEST_POSTGRES_PORT");
        if (port != null && !port.isBlank()) {
            properties.add("spring.datasource.url", () ->
                    "jdbc:postgresql://127.0.0.1:" + Integer.parseInt(port) + "/ticket_purchase_lock_test");
            properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            properties.add("spring.datasource.username", () -> "ticket_test");
            properties.add("spring.datasource.password", () -> "ticket_test");
        } else {
            properties.add("spring.datasource.url", () ->
                    "jdbc:h2:mem:purchase-lock;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        }
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @ParameterizedTest
    @CsvSource({"PER_EVENT,false,false", "PER_EVENT,true,false", "PER_GROUP,false,false",
            "PER_GROUP,true,false", "PER_EVENT,false,true", "PER_GROUP,false,true"})
    @DisplayName("동일 제한 범위는 최초 행 생성부터 직렬화하고 커밋 또는 롤백 결과로 매수를 검증한다")
    void concurrent_reservations_obey_limit(TicketLimitScope scope, boolean existingLock, boolean rollback)
            throws Exception {
        Fixture fixture = fixture(scope, scope == TicketLimitScope.PER_GROUP, false);
        String scopeKey = scope == TicketLimitScope.PER_GROUP
                ? fixture.group() : fixture.first().getEventId().toString();
        if (existingLock) {
            transaction().executeWithoutResult(status -> locks.acquire(fixture.user(), scope, scopeKey));
        }

        boolean secondSucceeded = concurrent(fixture, rollback, true);

        assertThat(secondSucceeded).isEqualTo(rollback);
        transaction().executeWithoutResult(status -> {
            Event event = events.findById(fixture.first().getEventId()).orElseThrow();
            List<TicketStatus> statuses = List.of(TicketStatus.PENDING_PAYMENT, TicketStatus.PAID);
            long count = scope == TicketLimitScope.PER_GROUP
                    ? tickets.countByUserIdAndEvent_EventGroupCodeAndStatusIn(fixture.user(), fixture.group(), statuses)
                    : tickets.countByUserIdAndEventAndStatusIn(fixture.user(), event, statuses);
            assertThat(count).isEqualTo(1);
        });
        assertThat(jdbc.queryForObject("select ticket_count from ticket_purchase_locks where user_id = ?", Long.class, fixture.user())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from ticket_purchase_locks where user_id = ?",
                Long.class, fixture.user())).isEqualTo(1);
        Long rejectedSeat = (rollback ? fixture.first() : fixture.second()).getSeats().get(0).getId();
        assertThat(seats.findById(rejectedSeat).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    @DisplayName("다른 사용자는 같은 공연에서도 서로의 구매 잠금을 기다리지 않는다")
    void different_users_do_not_block_each_other() throws Exception {
        assertThat(concurrent(fixture(TicketLimitScope.PER_EVENT, false, true), false, false)).isTrue();
    }

    @Test
    @DisplayName("회차별 제한이면 같은 그룹의 다른 회차는 독립적으로 예매한다")
    void per_event_scope_keeps_different_events_independent() throws Exception {
        assertThat(concurrent(fixture(TicketLimitScope.PER_EVENT, true, false), false, false)).isTrue();
    }

    @Test
    @DisplayName("예매 트랜잭션 없이 구매 잠금만 획득하는 호출은 거부한다")
    void purchase_lock_requires_existing_transaction() {
        assertThatThrownBy(() -> locks.acquire("user", TicketLimitScope.PER_EVENT, "1"))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"CANCELLED", "EXPIRED"})
    @DisplayName("취소 또는 만료는 실제 유효 매수만 반환하고 재처리해도 중복 차감하지 않는다")
    void release_count_with_ticket_status(TicketStatus target) {
        Fixture fixture = fixture(TicketLimitScope.PER_EVENT, false, false);
        Long reservationId = transaction().execute(status -> reservations.insert(fixture.first()).getReservationId());
        transaction().executeWithoutResult(status -> {
            var reservation = reservations.selectById(reservationId);
            var selected = tickets.findByReservation(reservation);
            counts.release(reservation, selected);
            selected.forEach(ticket -> {
                if (target == TicketStatus.CANCELLED) ticket.cancel();
                else ticket.expire();
                ticket.getSeat().available();
            });
        });
        assertThat(counter(fixture)).isZero();
        transaction().executeWithoutResult(status -> {
            var reservation = reservations.selectById(reservationId);
            counts.release(reservation, tickets.findByReservation(reservation));
        });
        assertThat(counter(fixture)).isZero();
        transaction().executeWithoutResult(status -> reservations.insert(fixture.second()));
        assertThat(counter(fixture)).isEqualTo(1);
    }

    @Test
    @DisplayName("차감 이후 오류가 발생하면 카운트와 티켓 상태를 함께 롤백한다")
    void failed_cancellation_rolls_back_count() {
        Fixture fixture = fixture(TicketLimitScope.PER_EVENT, false, false);
        Long reservationId = transaction().execute(status -> reservations.insert(fixture.first()).getReservationId());
        transaction().executeWithoutResult(status -> {
            var reservation = reservations.selectById(reservationId);
            var selected = tickets.findByReservation(reservation);
            counts.release(reservation, selected);
            selected.forEach(Ticket::cancel);
            status.setRollbackOnly();
        });
        assertThat(counter(fixture)).isEqualTo(1);
        transaction().executeWithoutResult(status -> assertThat(tickets.findByReservation(
                reservations.selectById(reservationId))).extracting(Ticket::getStatus)
                .containsOnly(TicketStatus.PENDING_PAYMENT));
    }

    @Test
    @DisplayName("기존 티켓이 모두 취소된 사용자의 최초 카운트는 0으로 생성한다")
    void initialize_purchase_count_to_zero() {
        Fixture fixture = fixture(TicketLimitScope.PER_EVENT, false, false);
        Long reservationId = transaction().execute(status -> reservations.insert(fixture.first()).getReservationId());
        transaction().executeWithoutResult(status -> {
            var reservation = reservations.selectById(reservationId);
            var selected = tickets.findByReservation(reservation);
            counts.release(reservation, selected);
            selected.forEach(Ticket::cancel);
        });
        jdbc.update("delete from ticket_purchase_locks where user_id = ?", fixture.user());
        transaction().executeWithoutResult(status -> {
            var reservation = reservations.selectById(reservationId);
            counts.prepareStatusAdjustment(reservation, tickets.findByReservation(reservation));
        });
        assertThat(counter(fixture)).isZero();
        transaction().executeWithoutResult(status -> reservations.insert(fixture.second()));
        assertThat(counter(fixture)).isEqualTo(1);
    }

    @Test
    @DisplayName("관리자가 취소 티켓을 복구하면 카운트도 증가하고 결제 완료 전환은 중복 증가하지 않는다")
    void status_adjustment_tracks_active_count_changes() {
        Fixture fixture = fixture(TicketLimitScope.PER_EVENT, false, false);
        Long reservationId = transaction().execute(status -> reservations.insert(fixture.first()).getReservationId());
        transaction().executeWithoutResult(status -> {
            var reservation = reservations.selectById(reservationId);
            var selected = tickets.findByReservation(reservation);
            counts.prepareStatusAdjustment(reservation, selected);
            long before = TicketPurchaseCountService.activeCount(selected);
            selected.forEach(Ticket::cancel);
            counts.applyStatusAdjustment(reservation, before, selected);
        });
        assertThat(counter(fixture)).isZero();
        transaction().executeWithoutResult(status -> {
            var reservation = reservations.selectById(reservationId);
            var selected = tickets.findByReservation(reservation);
            counts.prepareStatusAdjustment(reservation, selected);
            long before = TicketPurchaseCountService.activeCount(selected);
            selected.forEach(Ticket::paid);
            counts.applyStatusAdjustment(reservation, before, selected);
        });
        assertThat(counter(fixture)).isEqualTo(1);
    }

    private long counter(Fixture fixture) {
        return jdbc.queryForObject("select ticket_count from ticket_purchase_locks where user_id = ?",
                Long.class, fixture.user());
    }

    @Test
    @DisplayName("같은 계정의 두 요청이 각각 1매씩 확정하면 2매 제한 안에서 모두 성공한다")
    void concurrent_single_tickets_both_succeed_with_limit_two() throws Exception {
        Fixture fixture = fixture(TicketLimitScope.PER_EVENT, false, false, 2);
        assertThat(concurrent(fixture, false, true)).isTrue();
        assertThat(counter(fixture)).isEqualTo(2);
    }

    @Test
    @DisplayName("부분 취소는 선택한 매수만 차감하고 나머지 결제 대기 매수는 유지한다")
    void partial_cancellation_releases_only_selected_tickets() {
        Fixture fixture = fixture(TicketLimitScope.PER_EVENT, false, false, 2);
        var request = InsertReservationRequest.builder().orderId("order-" + UUID.randomUUID())
                .userId(fixture.user()).eventId(fixture.first().getEventId())
                .seats(List.of(fixture.first().getSeats().get(0), fixture.second().getSeats().get(0))).build();
        Long reservationId = transaction().execute(status -> reservations.insert(request).getReservationId());
        assertThat(counter(fixture)).isEqualTo(2);
        transaction().executeWithoutResult(status -> {
            var reservation = reservations.selectById(reservationId);
            var selected = List.of(tickets.findByReservation(reservation).get(0));
            counts.release(reservation, selected);
            selected.forEach(Ticket::cancel);
        });
        assertThat(counter(fixture)).isEqualTo(1);
    }

    private boolean concurrent(Fixture fixture, boolean rollbackFirst, boolean shouldWait) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstInserted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> transaction().executeWithoutResult(status -> {
                reservations.insert(fixture.first());
                firstInserted.countDown();
                await(releaseFirst);
                if (rollbackFirst) status.setRollbackOnly();
            }));
            assertThat(firstInserted.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> {
                try {
                    transaction().executeWithoutResult(status -> {
                        secondStarted.countDown();
                        reservations.insert(fixture.second());
                    });
                    return true;
                } catch (TicketLimitExceededException e) {
                    return false;
                }
            });
            assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
            if (shouldWait) {
                assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
            } else {
                assertThat(second.get(5, TimeUnit.SECONDS)).isTrue();
            }
            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            return second.get(10, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Fixture fixture(TicketLimitScope scope, boolean differentEvents, boolean differentUsers) {
        return fixture(scope, differentEvents, differentUsers, 1);
    }

    private Fixture fixture(TicketLimitScope scope, boolean differentEvents, boolean differentUsers, int limit) {
        String user = "user-" + UUID.randomUUID();
        String group = "group-" + UUID.randomUUID();
        return transaction().execute(status -> {
            Event first = events.save(event(scope, group, limit));
            Event second = differentEvents ? events.save(event(scope, group, limit)) : first;
            Seat a = seats.save(seat(first, 1));
            Seat b = seats.save(seat(second, 2));
            return new Fixture(user, group, request(user, first, a),
                    request(differentUsers ? "other-" + UUID.randomUUID() : user, second, b));
        });
    }

    private Event event(TicketLimitScope scope, String group, int limit) {
        return Event.builder().artistName("테스트 가수").title("동시 예매 테스트").venue("테스트 공연장")
                .eventGroupCode(group).ticketLimitScope(scope).maxTicketsPerPerson(limit)
                .eventDateTime(LocalDateTime.now().plusDays(7)).totalSeats(2).availableSeats(2)
                .status(EventStatus.ON_SALE).build();
    }

    private Seat seat(Event event, int col) {
        return Seat.builder().event(event).zone("VIP").seatRow(1).seatCol(col)
                .grade(SeatGrade.VIP).price(10000).status(SeatStatus.AVAILABLE).build();
    }

    private InsertReservationRequest request(String user, Event event, Seat seat) {
        return InsertReservationRequest.builder().orderId("order-" + UUID.randomUUID()).userId(user)
                .eventId(event.getEventId()).seats(List.of(SeatInfo.builder().id(seat.getSeatId())
                        .zone(seat.getZone()).row(seat.getSeatRow()).col(seat.getSeatCol()).build())).build();
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("잠금 대기 시간 초과");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("잠금 테스트 대기 중 인터럽트 발생", e);
        }
    }

    private record Fixture(String user, String group, InsertReservationRequest first, InsertReservationRequest second) { }
}
