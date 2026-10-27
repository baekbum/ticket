package dev.bum.ticket_service.jpa.coupon;

import dev.bum.common.service.ticket.coupon.coupon.dto.UpdateUserCouponRequest;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponDiscountType;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponStatus;
import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.reservation.dto.InsertReservationRequest;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import dev.bum.ticket_service.config.QuerydslConfig;
import dev.bum.ticket_service.jpa.area.AreaRepositoryImpl;
import dev.bum.ticket_service.jpa.coupon.coupon.Coupon;
import dev.bum.ticket_service.jpa.coupon.coupon.CouponJpaRepository;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCoupon;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCouponJpaRepository;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCouponRepositoryImpl;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventJpaRepository;
import dev.bum.ticket_service.jpa.event.event.EventRepositoryImpl;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepositoryImpl;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatJpaRepository;
import dev.bum.ticket_service.jpa.seat.SeatRepositoryImpl;
import dev.bum.ticket_service.jpa.ticket.TicketRepositoryImpl;
import dev.bum.ticket_service.service.ticket.TicketPurchaseCountService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
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

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ReservationRepositoryImpl.class, UserCouponRepositoryImpl.class, TicketRepositoryImpl.class,
        SeatRepositoryImpl.class, AreaRepositoryImpl.class, EventRepositoryImpl.class,
        QuerydslConfig.class, TicketPurchaseCountService.class})
class UserCouponConcurrencyIntegrationTest {

    @Autowired private ReservationRepositoryImpl reservations;
    @Autowired private UserCouponRepositoryImpl coupons;
    @Autowired private UserCouponJpaRepository couponRows;
    @Autowired private CouponJpaRepository policies;
    @Autowired private EventJpaRepository events;
    @Autowired private SeatJpaRepository seats;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String port = System.getenv("TICKET_COUPON_TEST_POSTGRES_PORT");
        if (port != null && !port.isBlank()) {
            // 기존 개발 DB 대신 쿠폰 검증 전용 DB에서만 create-drop을 허용한다.
            properties.add("spring.datasource.url", () ->
                    "jdbc:postgresql://127.0.0.1:" + Integer.parseInt(port) + "/ticket_coupon_atomicity_test");
            properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            properties.add("spring.datasource.username", () -> "ticket_test");
            properties.add("spring.datasource.password", () -> "ticket_test");
        } else {
            properties.add("spring.datasource.url", () ->
                    "jdbc:h2:mem:coupon-atomicity;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        }
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sameCouponAcrossDifferentEventsWaitsAndRevalidatesAfterCommitOrRollback(boolean rollback)
            throws Exception {
        Fixture fixture = fixture();
        CountDownLatch cached = new CountDownLatch(1);
        CountDownLatch startSecond = new CountDownLatch(1);
        CountDownLatch firstHolding = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var second = pool.submit(() -> {
                    try {
                        return transaction().execute(status -> {
                            // 첫 요청보다 먼저 읽은 ISSUED가 영속성 컨텍스트에 남아 있는 상황도 검증한다.
                            assertThat(couponRows.findById(fixture.couponId()).orElseThrow().getStatus())
                                    .isEqualTo(UserCouponStatus.ISSUED);
                            cached.countDown();
                            await(startSecond);
                            secondStarted.countDown();
                            reservations.insert(fixture.second());
                            return true;
                        });
                    } catch (IllegalArgumentException e) {
                        assertThat(e).hasMessage("이미 사용했거나 사용할 수 없는 쿠폰입니다.");
                        return false;
                    }
                });
                await(cached);
                var first = pool.submit(() -> transaction().execute(status -> {
                    reservations.insert(fixture.first());
                    entityManager.flush();
                    firstHolding.countDown();
                    await(releaseFirst);
                    if (rollback) status.setRollbackOnly();
                    return true;
                }));
                await(firstHolding);
                startSecond.countDown();
                await(secondStarted);
                assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                releaseFirst.countDown();
                first.get(10, TimeUnit.SECONDS);
                assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(rollback);
            } finally {
                startSecond.countDown();
                releaseFirst.countDown();
            }
        }

        assertThat(couponRows.findById(fixture.couponId()).orElseThrow().getStatus())
                .isEqualTo(UserCouponStatus.USED);
        assertThat(discountCount(fixture)).isEqualTo(1);
        assertThat(ticketCount(fixture)).isEqualTo(1);
        Long rejectedSeat = (rollback ? fixture.first() : fixture.second()).getSeats().getFirst().getId();
        assertThat(seats.findById(rejectedSeat).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void failureAfterFlushRollsBackCouponReservationDiscountSeatAndPurchaseCount() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
            reservations.insert(fixture.first());
            entityManager.flush();
            throw new IllegalStateException("예매 저장 이후 실패");
        })).isInstanceOf(IllegalStateException.class);

        UserCoupon coupon = couponRows.findById(fixture.couponId()).orElseThrow();
        assertThat(coupon.getStatus()).isEqualTo(UserCouponStatus.ISSUED);
        assertThat(coupon.getUsedAt()).isNull();
        assertThat(discountCount(fixture)).isZero();
        assertThat(ticketCount(fixture)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from reservations where order_id = ?",
                Long.class, fixture.first().getOrderId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from ticket_purchase_locks where user_id = ?",
                Long.class, fixture.first().getUserId())).isZero();
        assertThat(seats.findById(fixture.first().getSeats().getFirst().getId()).orElseThrow().getStatus())
                .isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void restoreRefreshesCachedCouponAndDoesNotOverwriteAdministratorCancellation() throws Exception {
        Fixture fixture = fixture();
        transaction().executeWithoutResult(status ->
                coupons.selectByIdForUpdate(fixture.couponId()).use(LocalDateTime.now()));
        CountDownLatch cached = new CountDownLatch(1);
        CountDownLatch changed = new CountDownLatch(1);

        try (var pool = Executors.newSingleThreadExecutor()) {
            var restore = pool.submit(() -> transaction().executeWithoutResult(status -> {
                UserCoupon stale = couponRows.findById(fixture.couponId()).orElseThrow();
                assertThat(stale.getStatus()).isEqualTo(UserCouponStatus.USED);
                cached.countDown();
                await(changed);
                coupons.lockForUpdate(stale);
                if (stale.getStatus() == UserCouponStatus.USED) stale.restore(LocalDateTime.now());
            }));
            try {
                await(cached);
                transaction().executeWithoutResult(status -> {
                    UserCoupon coupon = coupons.selectByIdForUpdate(fixture.couponId());
                    coupon.update(UpdateUserCouponRequest.builder().status(UserCouponStatus.CANCELLED).build());
                });
            } finally {
                changed.countDown();
            }
            restore.get(10, TimeUnit.SECONDS);
        }

        assertThat(couponRows.findById(fixture.couponId()).orElseThrow().getStatus())
                .isEqualTo(UserCouponStatus.CANCELLED);
    }

    @Test
    void expirationWaitsForUseAndDoesNotExpireCouponCommittedAsUsed() throws Exception {
        Fixture fixture = fixture();
        LocalDateTime now = LocalDateTime.now();
        transaction().executeWithoutResult(status -> coupons.selectByIdForUpdate(fixture.couponId())
                .update(UpdateUserCouponRequest.builder().expiresAt(now.minusMinutes(1)).build()));
        CountDownLatch firstHolding = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch expirationStarted = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var use = pool.submit(() -> transaction().executeWithoutResult(status -> {
                    coupons.selectByIdForUpdate(fixture.couponId()).use(now.minusMinutes(2));
                    entityManager.flush();
                    firstHolding.countDown();
                    await(releaseFirst);
                }));
                await(firstHolding);
                var expire = pool.submit(() -> transaction().execute(status -> {
                    expirationStarted.countDown();
                    return coupons.expireExpiredUserCoupons(now);
                }));
                await(expirationStarted);
                assertThatThrownBy(() -> expire.get(300, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                releaseFirst.countDown();
                use.get(10, TimeUnit.SECONDS);
                assertThat(expire.get(10, TimeUnit.SECONDS)).isZero();
            } finally {
                releaseFirst.countDown();
            }
        }
        assertThat(couponRows.findById(fixture.couponId()).orElseThrow().getStatus())
                .isEqualTo(UserCouponStatus.USED);
    }

    @Test
    void lockingRequiresExistingTransaction() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> coupons.selectByIdForUpdate(fixture.couponId()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private Fixture fixture() {
        return transaction().execute(status -> {
            String suffix = UUID.randomUUID().toString();
            String user = "coupon-" + suffix;
            LocalDateTime now = LocalDateTime.now();
            Coupon policy = policies.save(Coupon.builder()
                    .name("동시 사용 검증").code(suffix).discountType(CouponDiscountType.FIXED_AMOUNT)
                    .discountValue(1000).minOrderAmount(10000).validFrom(now.minusDays(1))
                    .validUntil(now.plusDays(1)).status(CouponStatus.ACTIVE).build());
            UserCoupon coupon = couponRows.save(UserCoupon.builder().userId(user).coupon(policy)
                    .status(UserCouponStatus.ISSUED).issuedAt(now.minusHours(1)).expiresAt(now.plusDays(1)).build());
            return new Fixture(coupon.getUserCouponId(), request(user, suffix + "-1", coupon),
                    request(user, suffix + "-2", coupon));
        });
    }

    private InsertReservationRequest request(String user, String orderId, UserCoupon coupon) {
        Event event = events.save(Event.builder().artistName("가수").title(orderId).venue("공연장")
                .eventDateTime(LocalDateTime.now().plusDays(10)).totalSeats(1).availableSeats(1)
                .status(EventStatus.ON_SALE).maxTicketsPerPerson(2).build());
        Seat seat = seats.save(Seat.builder().event(event).zone("VIP").seatRow(1).seatCol(1)
                .grade(SeatGrade.VIP).price(20000).status(SeatStatus.AVAILABLE).build());
        return InsertReservationRequest.builder().orderId(orderId).userId(user).eventId(event.getEventId())
                .userCouponId(coupon.getUserCouponId()).seats(List.of(SeatInfo.builder()
                        .id(seat.getSeatId()).zone("VIP").row(1).col(1).build())).build();
    }

    private long discountCount(Fixture fixture) {
        return jdbc.queryForObject("select count(*) from reservation_discounts where user_coupon_id = ?",
                Long.class, fixture.couponId());
    }

    private long ticketCount(Fixture fixture) {
        return jdbc.queryForObject("select count(*) from tickets where user_id = ?",
                Long.class, fixture.first().getUserId());
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactions);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("동시성 검증 대기 시간 초과");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private record Fixture(Long couponId, InsertReservationRequest first, InsertReservationRequest second) {}
}
