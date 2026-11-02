package dev.bum.ticket_service.service.payment.virtualAccount;

import dev.bum.common.kafka.payment.VirtualAccountExpiredEvent;
import dev.bum.common.service.ticket.event.event.enums.*;
import dev.bum.common.service.ticket.payment.dto.*;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.*;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayVirtualAccountClient;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCouponRepository;
import dev.bum.ticket_service.jpa.event.event.*;
import dev.bum.ticket_service.jpa.payment.payment.*;
import dev.bum.ticket_service.jpa.payment.virtualAccount.*;
import dev.bum.ticket_service.jpa.reservation.reservation.*;
import dev.bum.ticket_service.jpa.seat.*;
import dev.bum.ticket_service.jpa.ticket.*;
import dev.bum.ticket_service.service.payment.payment.*;
import dev.bum.ticket_service.service.payment.refund.*;
import dev.bum.ticket_service.service.reservation.reservation.ReservationCancellationService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import dev.bum.ticket_service.service.ticket.TicketPurchaseCountService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false)
@ContextConfiguration(classes = VirtualAccountCancelOutboxIntegrationTest.PersistenceConfig.class)
@Import({ReservationCancellationService.class, TicketPurchaseCountService.class, TicketRepositoryImpl.class,
        VirtualAccountCancelOutboxService.class, VirtualAccountCancelOutboxPublisher.class,
        VirtualAccountCancelOutboxDispatcher.class, VirtualAccountPaymentLockService.class,
        VirtualAccountPaymentService.class, PaymentCompletionService.class, PaymentExpirationService.class,
        VirtualAccountReconciliationCompletionService.class, VirtualAccountReconciliationValidator.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VirtualAccountCancelOutboxIntegrationTest {
    @Configuration @EnableAutoConfiguration
    @EntityScan(basePackages = "dev.bum.ticket_service.jpa")
    @EnableJpaRepositories(basePackages = "dev.bum.ticket_service.jpa")
    static class PersistenceConfig { }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:h2:mem:va-cancellation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        properties.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        properties.add("spring.datasource.username", () -> "sa");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired ReservationCancellationService cancellation;
    @Autowired VirtualAccountPaymentService deposits;
    @Autowired VirtualAccountReconciliationCompletionService reconciliation;
    @Autowired VirtualAccountCancelOutboxPublisher publisher;
    @Autowired VirtualAccountCancelOutboxDispatcher dispatcher;
    @Autowired VirtualAccountCancelOutboxService enqueue;
    @MockitoSpyBean VirtualAccountCancelOutboxJpaRepository outbox;
    @Autowired ReservationJpaRepository reservations;
    @Autowired PaymentJpaRepository payments;
    @Autowired TicketJpaRepository tickets;
    @Autowired SeatJpaRepository seats;
    @Autowired EventJpaRepository events;
    @Autowired TicketPurchaseLockJpaRepository counts;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @MockitoBean Clock clock;
    @MockitoBean SeatCacheService cache;
    @MockitoBean UserCouponRepository coupons;
    @MockitoBean PaymentRefundProcessService refunds;
    @MockitoBean CardPaymentRefundService cardRefunds;
    @MockitoBean VirtualAccountPaymentRefundService bankRefunds;
    @MockitoBean PaymentGatewayVirtualAccountClient gateway;
    Fixture fixture;

    record Fixture(Long reservationId, Long paymentId, Long ticketId, Long seatId) { }

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-10-27T03:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(refunds.inspectRequest(any(), anyList(), anyBoolean(), any()))
                .thenReturn(new PaymentRefundRequestContext(null, false));
        doAnswer(invocation -> {
            var request = invocation.getArgument(0, GatewayVirtualAccountCancelRequest.class);
            return new GatewayVirtualAccountCancelResponse(request.paymentNo(), request.bankName(),
                    request.accountNumber(), request.amount(), GatewayVirtualAccountStatus.CANCELLED);
        }).when(gateway).cancel(any());
        tx().executeWithoutResult(status -> {
            outbox.deleteAll();
            payments.deleteAll();
            tickets.deleteAll();
            reservations.deleteAll();
            seats.deleteAll();
            counts.deleteAll();
            events.deleteAll();
        });
        fixture = tx().execute(status -> {
            Event event = events.save(Event.builder().artistName("artist").title("event").venue("venue")
                    .eventDateTime(LocalDateTime.of(2026,11,1,18,0)).cancelDeadlineAt(LocalDateTime.of(2026,10,31,18,0))
                    .totalSeats(10).availableSeats(9).status(EventStatus.ON_SALE).maxTicketsPerPerson(4).build());
            Seat seat = seats.save(Seat.builder().event(event).zone("A").seatRow(1).seatCol(1)
                    .grade(SeatGrade.VIP).price(10000).status(SeatStatus.LOCKED).build());
            Reservation reservation = reservations.save(Reservation.builder().event(event).userId("owner")
                    .orderId("ORDER-1").status(ReservationStatus.PENDING_PAYMENT).reservedAt(LocalDateTime.now()).build());
            Ticket ticket = tickets.save(Ticket.builder().event(event).reservation(reservation).userId("owner")
                    .seat(seat).status(TicketStatus.PENDING_PAYMENT).build());
            Payment payment = Payment.builder().reservation(reservation).paymentNo("PAY-1")
                    .method(PaymentMethod.BANK_TRANSFER).status(PaymentStatus.WAITING_DEPOSIT).amount(10000)
                    .idempotencyKey("checkout-1").requestedAt(LocalDateTime.now()).build();
            payment.waitDeposit(BankCompany.KB.getBankName(), "1111-123456", LocalDateTime.of(2026,10,28,23,59));
            payments.saveAndFlush(payment);
            jdbc.update("insert into ticket_purchase_locks(user_id, limit_scope, scope_key, ticket_count, created_at) values (?, ?, ?, ?, ?)",
                    "owner", "PER_EVENT", event.getEventId().toString(), 1, LocalDateTime.now());
            return new Fixture(reservation.getReservationId(), payment.getPaymentId(), ticket.getTicketId(), seat.getSeatId());
        });
    }

    private TransactionTemplate tx() { return new TransactionTemplate(transactions); }
    private void cancelWithinTransaction() {
        cancellation.cancel(reservations.findByIdForUpdate(fixture.reservationId()).orElseThrow(),
                CancelReservationRequest.builder().selectedTicketIdList(List.of(fixture.ticketId())).build());
    }
    private void cancel() { tx().executeWithoutResult(status -> cancelWithinTransaction()); }
    private VirtualAccountCancelOutbox record() { return outbox.findByPaymentNo("PAY-1").orElseThrow(); }
    private void makeReady() { jdbc.update("update virtual_account_cancel_outbox set next_attempt_at = ?", LocalDateTime.now().minusMinutes(1)); }
    private void assertCancelled() {
        tx().executeWithoutResult(status -> {
            assertThat(reservations.findById(fixture.reservationId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.CANCELLED);
            assertThat(payments.findById(fixture.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(tickets.findById(fixture.ticketId()).orElseThrow().getStatus()).isEqualTo(TicketStatus.CANCELLED);
            assertThat(counts.findAll().getFirst().getTicketCount()).isZero();
        });
    }

    @Test void cancellationCommitsAllStatesAndOutboxWithoutCallingPg() {
        cancel();
        assertCancelled();
        assertThat(seats.findById(fixture.seatId()).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(record().getStatus()).isEqualTo(VirtualAccountCancelOutboxStatus.PENDING);
        verifyNoInteractions(gateway);
        // 같은 잠금 아래 다시 enqueue해도 작업은 하나다.
        tx().executeWithoutResult(status -> enqueue.enqueue(payments.findByIdForUpdate(fixture.paymentId()).orElseThrow()));
        assertThat(outbox.count()).isEqualTo(1);
        dispatcher.retryPendingEvents();
        assertThat(record().getStatus()).isEqualTo(VirtualAccountCancelOutboxStatus.DELIVERED);
    }

    @Test void rollbackAndOutboxSaveFailurePreserveUnpaidReservation() {
        assertThatThrownBy(() -> tx().executeWithoutResult(status -> {
            cancelWithinTransaction();
            throw new IllegalStateException("커밋 전 실패");
        })).hasMessage("커밋 전 실패");
        assertThat(outbox.count()).isZero();
        assertThat(payments.findById(fixture.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
        doThrow(new IllegalStateException("Outbox 저장 실패")).when(outbox).save(any(VirtualAccountCancelOutbox.class));
        assertThatThrownBy(this::cancel).hasMessage("Outbox 저장 실패");
        assertThat(outbox.count()).isZero();
        assertThat(reservations.findById(fixture.reservationId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(tickets.findById(fixture.ticketId()).orElseThrow().getStatus()).isEqualTo(TicketStatus.PENDING_PAYMENT);
        assertThat(seats.findById(fixture.seatId()).orElseThrow().getStatus()).isEqualTo(SeatStatus.LOCKED);
        assertThat(counts.findAll().getFirst().getTicketCount()).isEqualTo(1);
        verifyNoInteractions(gateway);
    }

    @Test void lostPgResponseRetriesTheSameRequestAndCompletes() {
        cancel();
        AtomicInteger attempts = new AtomicInteger();
        var payload = record().toRequest();
        doAnswer(invocation -> {
            assertThat(invocation.getArgument(0, GatewayVirtualAccountCancelRequest.class)).isEqualTo(payload);
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("PG 취소 후 응답 유실");
            return new GatewayVirtualAccountCancelResponse(payload.paymentNo(), payload.bankName(), payload.accountNumber(),
                    payload.amount(), GatewayVirtualAccountStatus.CANCELLED);
        }).when(gateway).cancel(any());
        dispatcher.retryPendingEvents();
        assertCancelled();
        assertThat(record().getStatus()).isEqualTo(VirtualAccountCancelOutboxStatus.PENDING);
        assertThat(record().getRetryCount()).isEqualTo(1);
        dispatcher.retryPendingEvents();
        assertThat(attempts).hasValue(1);
        makeReady();
        dispatcher.retryPendingEvents();
        assertThat(record().getStatus()).isEqualTo(VirtualAccountCancelOutboxStatus.DELIVERED);
        assertThat(record().getLastErrorType()).isNull();
        assertThat(attempts).hasValue(2);
    }

    @Test void mismatchedPgResponseDoesNotCompleteOutbox() {
        cancel();
        doReturn(new GatewayVirtualAccountCancelResponse("OTHER", BankCompany.KB.getBankName(),
                "1111-123456", BigDecimal.valueOf(10000), GatewayVirtualAccountStatus.CANCELLED)).when(gateway).cancel(any());
        dispatcher.retryPendingEvents();
        assertThat(record().getStatus()).isEqualTo(VirtualAccountCancelOutboxStatus.PENDING);
        assertThat(record().getRetryCount()).isEqualTo(1);
    }

    @Test void deliveryCommitFailureLeavesWorkForSafeRetry() {
        cancel();
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { throw new IllegalStateException("완료 커밋 실패"); }
            });
            var payload = record().toRequest();
            return new GatewayVirtualAccountCancelResponse(payload.paymentNo(), payload.bankName(), payload.accountNumber(),
                    payload.amount(), GatewayVirtualAccountStatus.CANCELLED);
        }).when(gateway).cancel(any());
        dispatcher.retryPendingEvents();
        assertThat(record().getStatus()).isEqualTo(VirtualAccountCancelOutboxStatus.PENDING);
        dispatcher.retryPendingEvents();
        assertThat(record().getStatus()).isEqualTo(VirtualAccountCancelOutboxStatus.DELIVERED);
        assertThat(attempts).hasValue(2);
    }

    @Test void lateDepositAndExpirationCannotReviveCancellationOrReleaseResoldSeat() {
        cancel();
        Long buyerTicket = tx().execute(status -> {
            Seat seat = seats.findByIdForUpdate(fixture.seatId()).orElseThrow();
            seat.reserved();
            Reservation buyer = reservations.save(Reservation.builder().event(seat.getEvent()).userId("buyer")
                    .orderId("ORDER-2").status(ReservationStatus.PAID).reservedAt(LocalDateTime.now()).build());
            return tickets.save(Ticket.builder().reservation(buyer).event(seat.getEvent()).seat(seat).userId("buyer")
                    .status(TicketStatus.PAID).build()).getTicketId();
        });
        assertThat(deposits.completeDepositFromGateway(depositRequest()).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(deposits.expireFromGateway(VirtualAccountExpiredEvent.builder().paymentNo("PAY-1")
                .bankCompany(BankCompany.KB).bankName(BankCompany.KB.getBankName()).accountNumber("1111-123456")
                .amount(BigDecimal.valueOf(10000)).expiredAt(LocalDateTime.now()).build()).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertCancelled();
        assertThat(tickets.findById(buyerTicket).orElseThrow().getStatus()).isEqualTo(TicketStatus.PAID);
        assertThat(seats.findById(fixture.seatId()).orElseThrow().getStatus()).isEqualTo(SeatStatus.RESERVED);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void depositAndManualCompletionWaitForCancellation(boolean manualCompletion) throws Exception {
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var cancel = pool.submit(() -> tx().executeWithoutResult(status -> {
                    cancelWithinTransaction();
                    locked.countDown();
                    await(release);
                }));
                await(locked);
                var deposit = pool.submit(() -> manualCompletion
                        ? reconciliation.complete("PAY-1", new GatewayVirtualAccountStatusResponse(
                                "PAY-1", BankCompany.KB, BankCompany.KB.getBankName(), "1111-123456", "입금자",
                                BigDecimal.valueOf(10000), LocalDateTime.of(2026,10,28,23,59),
                                depositRequest().getDepositedAt(), GatewayVirtualAccountStatus.DEPOSITED))
                        : deposits.completeDepositFromGateway(depositRequest()));
                assertThatThrownBy(() -> deposit.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                cancel.get(10, TimeUnit.SECONDS);
                if (manualCompletion) {
                    assertThatThrownBy(() -> deposit.get(10, TimeUnit.SECONDS))
                            .hasRootCauseMessage("Ticket 결제가 입금 대기 상태가 아닙니다. 만료·취소 건은 별도 조치가 필요합니다.");
                } else {
                    assertThat(deposit.get(10, TimeUnit.SECONDS).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
                }
            } finally { release.countDown(); }
        }
        assertCancelled();
    }

    @Test void concurrentWorkersDeliverOnlyOnce() throws Exception {
        cancel();
        CountDownLatch sending = new CountDownLatch(1), release = new CountDownLatch(1);
        Long id = record().getOutboxId();
        var payload = record().toRequest();
        doAnswer(invocation -> {
            sending.countDown(); await(release);
            return new GatewayVirtualAccountCancelResponse(payload.paymentNo(), payload.bankName(), payload.accountNumber(),
                    payload.amount(), GatewayVirtualAccountStatus.CANCELLED);
        }).when(gateway).cancel(any());
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var first = pool.submit(() -> publisher.deliver(id));
                await(sending);
                var second = pool.submit(() -> publisher.deliver(id));
                assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown(); first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
            } finally { release.countDown(); }
        }
        verify(gateway, times(1)).cancel(any());
        assertThat(record().getStatus()).isEqualTo(VirtualAccountCancelOutboxStatus.DELIVERED);
    }

    private VirtualAccountDepositCompleteRequest depositRequest() {
        return VirtualAccountDepositCompleteRequest.builder().paymentNo("PAY-1").bankCompany(BankCompany.KB)
                .bankName(BankCompany.KB.getBankName()).accountNumber("1111-123456").depositorName("입금자")
                .amount(BigDecimal.valueOf(10000)).depositedAt(LocalDateTime.of(2026,10,27,12,0)).build();
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("동시성 테스트 대기 시간 초과"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
