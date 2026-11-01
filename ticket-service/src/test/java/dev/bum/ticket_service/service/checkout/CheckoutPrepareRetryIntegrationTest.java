package dev.bum.ticket_service.service.checkout;

import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareRequest;
import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareResponse;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayCardClient;
import dev.bum.ticket_service.jpa.checkout.CheckoutAttempt;
import dev.bum.ticket_service.jpa.checkout.CheckoutAttemptJpaRepository;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatJpaRepository;
import dev.bum.ticket_service.service.checkout.payment.CheckoutPaymentService;
import dev.bum.ticket_service.service.event.event.EventBookingGuard;
import dev.bum.ticket_service.service.queue.QueueAccessService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/** 실제 DB 트랜잭션과 별도 연결에서의 재조회로 재시도 복구를 검증한다. */
@DataJpaTest(showSql = false)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CheckoutService.class, CheckoutIdempotencyKeyGenerator.class, EventBookingGuard.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "CHECKOUT_TEST_POSTGRES_URL", matches = "jdbc:postgresql:.*")
class CheckoutPrepareRetryIntegrationTest {

    @Autowired CheckoutService checkout;
    @Autowired CheckoutAttemptJpaRepository attempts;
    @Autowired EventJpaRepository events;
    @Autowired SeatJpaRepository seats;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @MockitoBean SeatCacheService seatCache;
    @MockitoBean QueueAccessService queue;
    @MockitoBean ReservationRepository reservations;
    @MockitoBean CheckoutPaymentService payments;
    @MockitoBean PaymentGatewayCardClient gateway;
    @MockitoBean Clock clock;
    @MockitoSpyBean EventBookingGuard bookingGuard;
    @MockitoSpyBean CheckoutIdempotencyKeyGenerator keyGenerator;

    private final AtomicReference<LocalDateTime> leaseEnd = new AtomicReference<>();
    private Event event;
    private List<SeatInfo> selectedSeats;

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        String url = System.getenv("CHECKOUT_TEST_POSTGRES_URL");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:\\d+/checkout_prepare_test")) {
            throw new IllegalArgumentException("분리된 checkout_prepare_test DB가 필요합니다.");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @BeforeEach
    void fixture() {
        attempts.deleteAll();
        seats.deleteAll();
        events.deleteAll();
        when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        when(clock.instant()).thenAnswer(invocation -> Instant.now());
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        leaseEnd.set(now.plusMinutes(10).withNano(123456789));
        event = events.saveAndFlush(Event.builder().artistName("artist").title("checkout retry").venue("venue")
                .eventDateTime(now.plusDays(7)).saleStartAt(now.minusDays(1)).saleEndAt(now.plusDays(1))
                .status(EventStatus.ON_SALE).maxTicketsPerPerson(4).totalSeats(10).availableSeats(10).build());
        Seat first = seats.saveAndFlush(seat(1));
        Seat second = seats.saveAndFlush(seat(2));
        selectedSeats = List.of(info(first), info(second));
        when(seatCache.validateOccupiedSeat(anyLong(), anyString(), anyString(), anyList()))
                .thenAnswer(invocation -> leaseEnd.get());
    }

    @Test
    void retry_after_a_lost_response_restores_the_key_seats_and_original_timestamps() {
        CheckoutPrepareResponse first = prepare(request("order-1", selectedSeats));
        leaseEnd.set(leaseEnd.get().plusMinutes(20));

        CheckoutPrepareResponse replay = prepare(request("order-1", selectedSeats.reversed()));

        assertSameResponse(first, replay);
        assertThat(attempts.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from checkout_attempt_seats", Integer.class)).isEqualTo(2);
        CheckoutAttempt saved = new TransactionTemplate(transactions).execute(status ->
                attempts.findByOrderIdForUpdate("order-1").orElseThrow());
        assertThat(saved.getCreatedAt()).isEqualTo(first.getPreparedAt());
        assertThat(saved.getExpiresAt()).isEqualTo(first.getExpiresAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "EVENT", "SEATS"})
    void an_existing_order_cannot_be_reused_by_a_different_request(String changed) {
        CheckoutPrepareResponse first = prepare(request("order-1", selectedSeats));
        CheckoutPrepareRequest replay = request("order-1", selectedSeats);
        String user = "user01";
        if ("USER".equals(changed)) user = "another-user";
        if ("EVENT".equals(changed)) replay.setEventId(event.getEventId() + 1);
        if ("SEATS".equals(changed)) replay.setSeats(List.of(selectedSeats.getFirst()));
        String authenticatedUser = user;
        clearInvocations(queue, seatCache);

        assertThatThrownBy(() -> checkout.prepare(authenticatedUser, "active-token", replay))
                .isInstanceOf("USER".equals(changed) ? AccessDeniedException.class : IllegalArgumentException.class);

        verifyNoInteractions(queue);
        assertThat(attempts.findAll()).singleElement().satisfies(attempt ->
                assertThat(attempt.getIdempotencyKey()).isEqualTo(first.getIdempotencyKey()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"EXPIRED", "CONFIRMED"})
    void expired_or_confirmed_attempts_cannot_be_prepared_again(String state) {
        prepare(request("order-1", selectedSeats));
        if ("EXPIRED".equals(state)) {
            jdbc.update("update checkout_attempts set expires_at = ? where order_id = ?",
                    Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)), "order-1");
        } else {
            jdbc.update("update checkout_attempts set status = 'CONFIRMED' where order_id = ?", "order-1");
        }

        assertThatThrownBy(() -> prepare(request("order-1", selectedSeats))).isInstanceOf(IllegalStateException.class);
        assertThat(attempts.count()).isEqualTo(1);
    }

    @Test
    void a_saved_attempt_does_not_bypass_the_sales_deadline() {
        CheckoutPrepareResponse first = prepare(request("order-1", selectedSeats));
        when(clock.instant()).thenReturn(event.getSaleEndAt().plusSeconds(1)
                .atZone(ZoneId.systemDefault()).toInstant());

        assertThatThrownBy(() -> prepare(request("order-1", selectedSeats)))
                .hasMessage("판매가 종료된 공연입니다.");
        assertThat(attempts.findAll()).singleElement().satisfies(attempt ->
                assertThat(attempt.getIdempotencyKey()).isEqualTo(first.getIdempotencyKey()));
    }

    @Test
    void concurrent_initial_requests_wait_at_insert_and_return_the_same_attempt() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        blockFirstEventValidation(entered, release);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CheckoutPrepareResponse> first = executor.submit(() -> prepare(request("order-1", selectedSeats)));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(attempts.count()).isZero();
            Future<CheckoutPrepareResponse> second = executor.submit(() ->
                    prepare(request("order-1", selectedSeats.reversed())));
            awaitWaitingInsert();
            assertThat(second.isDone()).isFalse();

            release.countDown();
            assertSameResponse(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(attempts.count()).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from checkout_attempt_seats", Integer.class)).isEqualTo(2);
            verify(keyGenerator, times(2)).generate();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void different_orders_can_prepare_concurrently_for_the_same_event() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        blockFirstEventValidation(entered, release);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CheckoutPrepareResponse> first = executor.submit(() ->
                    prepare(request("order-1", List.of(selectedSeats.getFirst()))));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            Future<CheckoutPrepareResponse> second = executor.submit(() ->
                    prepare(request("order-2", List.of(selectedSeats.getLast()))));

            assertThat(second.get(5, TimeUnit.SECONDS).getOrderId()).isEqualTo("order-2");
            assertThat(first.isDone()).isFalse();
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).getOrderId()).isEqualTo("order-1");
            assertThat(attempts.count()).isEqualTo(2);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void seat_insert_failure_rolls_back_the_parent_and_previously_inserted_seats() {
        jdbc.execute("ALTER TABLE checkout_attempt_seats ADD CONSTRAINT checkout_prepare_test_reject "
                + "CHECK (seat_id <> " + selectedSeats.getLast().getId() + ")");
        try {
            assertThatThrownBy(() -> prepare(request("order-fail", selectedSeats)))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(attempts.count()).isZero();
            assertThat(jdbc.queryForObject("select count(*) from checkout_attempt_seats", Integer.class)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE checkout_attempt_seats DROP CONSTRAINT checkout_prepare_test_reject");
        }
        assertThat(prepare(request("order-fail", selectedSeats)).getOrderId()).isEqualTo("order-fail");
        assertThat(attempts.count()).isEqualTo(1);
    }

    @Test
    void waiting_request_inserts_its_own_attempt_when_the_first_transaction_rolls_back() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        blockFirstEventValidation(entered, release, true);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CheckoutPrepareResponse> first = executor.submit(() -> prepare(request("order-1", selectedSeats)));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            Future<CheckoutPrepareResponse> second = executor.submit(() -> prepare(request("order-1", selectedSeats)));
            awaitWaitingInsert();
            assertThat(second.isDone()).isFalse();

            release.countDown();
            assertThatThrownBy(() -> first.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class);
            CheckoutPrepareResponse response = second.get(10, TimeUnit.SECONDS);
            assertThat(attempts.findAll()).singleElement().satisfies(attempt ->
                    assertThat(attempt.getIdempotencyKey()).isEqualTo(response.getIdempotencyKey()));
            assertThat(jdbc.queryForObject("select count(*) from checkout_attempt_seats", Integer.class)).isEqualTo(2);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void validation_failure_after_inserting_rolls_back_the_parent_and_seat_snapshot() {
        doThrow(new IllegalArgumentException("유효하지 않은 대기열 토큰"))
                .when(queue).validate(event.getEventId(), "user01", "active-token");

        assertThatThrownBy(() -> prepare(request("order-1", selectedSeats)))
                .hasMessage("유효하지 않은 대기열 토큰");

        assertThat(attempts.count()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from checkout_attempt_seats", Integer.class)).isZero();
    }

    @Test
    void insert_if_absent_returns_one_then_zero_and_preserves_the_original_snapshot() {
        CheckoutAttempt first = candidate("first-key", "order-1", "PAY-FIRST");
        CheckoutAttempt duplicate = CheckoutAttempt.prepare("unused-key", "another-user", "order-1",
                event.getEventId(), Set.of(selectedSeats.getFirst().getId()), "PAY-UNUSED", leaseEnd.get());
        TransactionTemplate transaction = new TransactionTemplate(transactions);

        Integer firstResult = transaction.execute(status -> attempts.insertIfAbsent(first));
        Integer duplicateResult = transaction.execute(status -> attempts.insertIfAbsent(duplicate));
        assertThat(firstResult).isEqualTo(1);
        assertThat(duplicateResult).isZero();
        transaction.executeWithoutResult(status -> {
            CheckoutAttempt saved = attempts.findByOrderIdForUpdate("order-1").orElseThrow();
            assertThat(saved.getIdempotencyKey()).isEqualTo(first.getIdempotencyKey());
            assertThat(saved.getUserId()).isEqualTo(first.getUserId());
            assertThat(saved.getPaymentNo()).isEqualTo(first.getPaymentNo());
            assertThat(saved.getSeatIds()).isEqualTo(first.getSeatIds());
            assertThat(saved.getCreatedAt()).isEqualTo(first.getCreatedAt());
        });
        assertThat(jdbc.queryForObject("select count(*) from checkout_attempt_seats", Integer.class)).isEqualTo(2);
    }

    @Test
    void a_different_unique_constraint_is_not_ignored() {
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> attempts.insertIfAbsent(candidate("key-1", "order-1", "PAY-SAME")));

        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                attempts.insertIfAbsent(candidate("key-2", "order-2", "PAY-SAME"))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(attempts.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from checkout_attempt_seats", Integer.class)).isEqualTo(2);
    }

    private CheckoutAttempt candidate(String key, String orderId, String paymentNo) {
        return CheckoutAttempt.prepare(key, "user01", orderId, event.getEventId(),
                selectedSeats.stream().map(SeatInfo::getId).collect(Collectors.toSet()),
                paymentNo, leaseEnd.get().truncatedTo(ChronoUnit.MICROS));
    }

    private void awaitWaitingInsert() {
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM pg_stat_activity
                        WHERE datname = current_database()
                          AND wait_event_type = 'Lock'
                          AND query LIKE '%INSERT INTO checkout_attempts%'
                        """, Integer.class)).isPositive());
    }

    private CheckoutPrepareResponse prepare(CheckoutPrepareRequest request) {
        return checkout.prepare("user01", "active-token", request);
    }

    private void blockFirstEventValidation(CountDownLatch entered, CountDownLatch release) {
        blockFirstEventValidation(entered, release, false);
    }

    private void blockFirstEventValidation(CountDownLatch entered, CountDownLatch release, boolean failFirst) {
        AtomicBoolean first = new AtomicBoolean(true);
        EventBookingGuard target = AopTestUtils.getUltimateTargetObject(bookingGuard);
        doAnswer(invocation -> {
            if (first.compareAndSet(true, false)) {
                entered.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("테스트 잠금 시간 초과");
                if (failFirst) throw new IllegalArgumentException("첫 요청의 검증 실패");
            }
            return invocation.callRealMethod();
        }).when(target).requireBookable(anyLong(), eq(true));
    }

    private CheckoutPrepareRequest request(String orderId, List<SeatInfo> requestedSeats) {
        return CheckoutPrepareRequest.builder().orderId(orderId).eventId(event.getEventId()).seats(requestedSeats).build();
    }

    private Seat seat(int col) {
        return Seat.builder().event(event).zone("VIP").seatRow(1).seatCol(col)
                .grade(SeatGrade.VIP).price(10000).status(SeatStatus.LOCKED).build();
    }

    private SeatInfo info(Seat seat) {
        return SeatInfo.builder().id(seat.getSeatId()).zone(seat.getZone()).row(seat.getSeatRow()).col(seat.getSeatCol()).build();
    }

    private void assertSameResponse(CheckoutPrepareResponse first, CheckoutPrepareResponse replay) {
        assertThat(replay.getEventId()).isEqualTo(first.getEventId());
        assertThat(replay.getOrderId()).isEqualTo(first.getOrderId());
        assertThat(replay.getIdempotencyKey()).isEqualTo(first.getIdempotencyKey());
        assertThat(replay.getPreparedAt()).isEqualTo(first.getPreparedAt());
        assertThat(replay.getExpiresAt()).isEqualTo(first.getExpiresAt());
        assertThat(replay.getSeats()).isEqualTo(first.getSeats());
        assertThat(replay.isPrepared()).isTrue();
    }
}
