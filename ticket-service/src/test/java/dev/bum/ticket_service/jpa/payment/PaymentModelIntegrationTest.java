package dev.bum.ticket_service.jpa.payment;

import dev.bum.ticket_service.service.checkout.payment.virtualAccount.VirtualAccountIssuanceCheckoutCommandService;

import dev.bum.common.service.ticket.coupon.coupon.enums.DiscountType;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.payment.dto.RefundAccountRequest;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountDepositCompleteRequest;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.ticket_service.jpa.event.event.*;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.jpa.payment.payment.PaymentHistory;
import dev.bum.ticket_service.jpa.payment.payment.PaymentHistoryJpaRepository;
import dev.bum.ticket_service.jpa.payment.payment.PaymentHistoryType;
import dev.bum.ticket_service.jpa.payment.payment.PaymentJpaRepository;
import dev.bum.ticket_service.jpa.payment.payment.PaymentProcessStage;
import dev.bum.ticket_service.jpa.payment.payment.PaymentSnapshot;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundHistory;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundHistoryJpaRepository;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundHistoryType;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundJpaRepository;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcess;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcessJpaRepository;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcessStatus;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundStatus;
import dev.bum.ticket_service.jpa.reservation.reservation.*;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscount;
import dev.bum.ticket_service.jpa.seat.*;
import dev.bum.ticket_service.jpa.ticket.*;
import dev.bum.ticket_service.service.payment.refund.PaymentRefundProcessService;
import dev.bum.ticket_service.service.payment.refund.PaymentRefundRollbackRecorder;
import dev.bum.ticket_service.service.payment.virtualAccount.VirtualAccountPaymentService;
import dev.bum.ticket_service.service.payment.payment.PaymentCompletionService;
import dev.bum.ticket_service.service.payment.payment.PaymentExpirationService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import dev.bum.ticket_service.service.ticket.TicketPurchaseCountService;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCouponRepository;
import dev.bum.common.service.paymentgateway.notification.CardPaymentNotification;
import dev.bum.common.service.paymentgateway.notification.CardPaymentNotificationResponse;
import dev.bum.ticket_service.service.payment.card.CardPaymentService;
import dev.bum.ticket_service.service.payment.payment.PaymentService;
import dev.bum.ticket_service.service.payment.notification.CardPaymentNotificationService;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@DataJpaTest
@ActiveProfiles("test")
@Import({PaymentRefundProcessService.class, PaymentRefundRollbackRecorder.class,
        dev.bum.ticket_service.service.checkout.payment.virtualAccount.VirtualAccountIssuanceCheckoutCommandService.class,
        dev.bum.ticket_service.service.payment.virtualAccount.VirtualAccountCancelOutboxService.class,
        VirtualAccountPaymentService.class,
        dev.bum.ticket_service.service.payment.virtualAccount.VirtualAccountPaymentLockService.class,
        PaymentCompletionService.class, PaymentExpirationService.class,
        CardPaymentService.class, PaymentService.class, CardPaymentNotificationService.class,
        dev.bum.ticket_service.service.payment.notification.BankDepositNotificationService.class})
class PaymentModelIntegrationTest {
    @MockitoBean Clock clock;

    @BeforeEach
    void configureTime() {
        given(clock.instant()).willReturn(Instant.parse("2026-10-15T03:00:00Z"));
        given(clock.getZone()).willReturn(ZoneId.of("Asia/Seoul"));
    }

    @Autowired PaymentRefundProcessService processService;
    @MockitoBean SeatCacheService seatCacheService;
    @MockitoBean UserCouponRepository userCouponRepository;

    @MockitoBean
    TicketPurchaseCountService purchaseCountService;

    @Autowired PaymentJpaRepository payments;
    @Autowired PaymentHistoryJpaRepository paymentHistories;
    @Autowired PaymentRefundProcessJpaRepository processes;
    @Autowired PaymentRefundHistoryJpaRepository refundHistories;
    @Autowired PaymentRefundJpaRepository refunds;
    @Autowired EventJpaRepository events;
    @Autowired ReservationJpaRepository reservations;
    @Autowired SeatJpaRepository seats;
    @Autowired TicketJpaRepository tickets;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired VirtualAccountPaymentService virtualAccountPayments;
    @Autowired CardPaymentNotificationService cardNotifications;
    @MockitoBean TicketRepository ticketRepository;
    @Autowired dev.bum.ticket_service.service.payment.notification.BankDepositNotificationService bankNotifications;

    @Test
    void standardBankNotificationUpdatesTheRealPaymentAndReplaysWithoutNewHistory() {
        Payment payment = payment("standard-bank-notification");
        payment.switchToBankTransfer();
        payment.planGatewayAccount(BankCompany.KB, LocalDateTime.now().plusDays(1));
        payment.bindGatewayPayment("20261109550e8400-e29b-41d4-a716-446655440002");
        payment.bindGatewayAccount(BankCompany.KB.getBankName(), "1111-123456", payment.getGatewayExpiresAt());
        payments.saveAndFlush(payment);
        given(ticketRepository.selectByReservation(payment.getReservation()))
                .willReturn(tickets.findByReservation(payment.getReservation()));
        var event = bankEvent(payment, "user");
        var first = bankNotifications.receive(event);
        payments.flush();
        assertThat(first.outcome()).isEqualTo(dev.bum.common.service.paymentgateway.notification.BankDepositNotificationResponse.Outcome.APPLIED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getReservation().getStatus()).isEqualTo(ReservationStatus.PAID);
        assertThat(tickets.findByReservation(payment.getReservation()))
                .allMatch(ticket -> ticket.getStatus().name().equals("PAID"))
                .allMatch(ticket -> ticket.getSeat().getStatus() == SeatStatus.RESERVED);
        long count = paymentHistories.count();
        assertThat(bankNotifications.receive(event)).isEqualTo(first);
        payments.flush();
        assertThat(paymentHistories.count()).isEqualTo(count);
    }

    @Test
    void standardBankNotificationCannotReviveCancelledReservationOrReclaimAvailableSeats() {
        Payment payment = payment("cancelled-bank-notification");
        payment.switchToBankTransfer();
        payment.planGatewayAccount(BankCompany.KB, LocalDateTime.now().plusDays(1));
        payment.bindGatewayPayment("20261109550e8400-e29b-41d4-a716-446655440002");
        payment.bindGatewayAccount(BankCompany.KB.getBankName(), "1111-123456", payment.getGatewayExpiresAt());
        payment.cancel();
        payment.getReservation().cancel();
        tickets.findByReservation(payment.getReservation()).forEach(ticket -> ticket.getSeat().available());
        payments.saveAndFlush(payment);
        long count = paymentHistories.count();
        assertThat(bankNotifications.receive(bankEvent(payment, "user")).outcome())
                .isEqualTo(dev.bum.common.service.paymentgateway.notification.BankDepositNotificationResponse.Outcome.RESERVATION_CANCELLED);
        payments.flush();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(payment.getReservation().getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(tickets.findByReservation(payment.getReservation()))
                .allMatch(ticket -> ticket.getSeat().getStatus() == SeatStatus.AVAILABLE);
        assertThat(paymentHistories.count()).isEqualTo(count);
    }

    @Test
    void standardBankNotificationRejectsAnotherBuyerBeforeCompletingThePayment() {
        Payment payment = payment("bank-wrong-owner");
        payment.switchToBankTransfer();
        payment.planGatewayAccount(BankCompany.KB, LocalDateTime.now().plusDays(1));
        payment.bindGatewayPayment("20261109550e8400-e29b-41d4-a716-446655440002");
        payment.bindGatewayAccount(BankCompany.KB.getBankName(), "1111-123456", payment.getGatewayExpiresAt());
        payments.saveAndFlush(payment);
        assertThatThrownBy(() -> bankNotifications.receive(bankEvent(payment, "another-user")))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
    }

    dev.bum.common.service.paymentgateway.notification.BankDepositNotification bankEvent(Payment payment, String buyer) {
        String id = "20261109550e8400-e29b-41d4-a716-446655440002";
        return new dev.bum.common.service.paymentgateway.notification.BankDepositNotification(1, id + ":DEPOSIT:INITIAL",
                1L, id, payment.getPaymentNo(), buyer, PaymentMethod.BANK_TRANSFER, PaymentStatus.PAID,
                new BigDecimal("12000"), "1111-123456", BankCompany.KB, "입금자", LocalDateTime.now().withNano(123456000));
    }

    @Test
    void standardCardNotificationUpdatesTheRealPaymentTicketsAndSeatsAndReplaysWithoutDuplicateHistory() {
        Payment payment = payments.saveAndFlush(payment("standard-card-notification"));
        given(ticketRepository.selectByReservation(payment.getReservation()))
                .willReturn(tickets.findByReservation(payment.getReservation()));
        String pgId = "20261109550e8400-e29b-41d4-a716-446655440000";
        payment.bindGatewayPayment(pgId);
        payments.flush();
        CardPaymentNotification event = new CardPaymentNotification(1, pgId + ":APPROVE:INITIAL", 1L, pgId,
                payment.getPaymentNo(), "user", PaymentMethod.CREDIT_CARD, PaymentStatus.PAID,
                new BigDecimal("12000"), "CARD-STANDARD", CardCompany.SHINHAN, "4111-****-****-1111",
                LocalDateTime.now());
        CardPaymentNotificationResponse first = cardNotifications.receive(event);
        payments.flush();
        assertThat(first.outcome()).isEqualTo(CardPaymentNotificationResponse.Outcome.ACCEPTED);
        assertThat(first.matches(event)).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getReservation().getStatus()).isEqualTo(ReservationStatus.PAID);
        assertThat(tickets.findByReservation(payment.getReservation()))
                .allMatch(ticket -> ticket.getStatus().name().equals("PAID"))
                .allMatch(ticket -> ticket.getSeat().getStatus() == SeatStatus.RESERVED);
        long historyCount = paymentHistories.count();
        CardPaymentNotificationResponse replay = cardNotifications.receive(event);
        payments.flush();
        assertThat(replay).isEqualTo(first);
        assertThat(paymentHistories.count()).isEqualTo(historyCount);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3000, 8000})
    void depositNotificationReplaysAfterPaymentOrRefundWithoutChangingHistory(int refundAmount) {
        Payment payment = payment("deposit-replay");
        payment.switchToBankTransfer();
        LocalDateTime expiresAt = LocalDateTime.of(2026, 8, 20, 23, 59, 59);
        payment.waitDeposit(BankCompany.KB.getBankName(), "1111-123456", expiresAt);
        payments.saveAndFlush(payment);
        given(ticketRepository.selectByReservation(payment.getReservation()))
                .willReturn(tickets.findByReservation(payment.getReservation()));
        VirtualAccountDepositCompleteRequest request = VirtualAccountDepositCompleteRequest.builder()
                .paymentNo(payment.getPaymentNo()).bankCompany(BankCompany.KB).bankName(BankCompany.KB.getBankName())
                .accountNumber("1111-123456").depositorName("입금자").amount(BigDecimal.valueOf(12000))
                .depositedAt(expiresAt.minusMinutes(1)).build();

        virtualAccountPayments.completeDepositFromGateway(request, payment.getReservation().getUserId());
        if (refundAmount > 0) payment.partialRefund(refundAmount);
        payments.flush();
        int historiesBeforeReplay = payment.getHistories().size();
        PaymentStatus statusBeforeReplay = payment.getStatus();

        var replay = virtualAccountPayments.completeDepositFromGateway(request, payment.getReservation().getUserId());
        assertThat(replay.getStatus()).isEqualTo(statusBeforeReplay);
        assertThat(payment.getHistories()).hasSize(historiesBeforeReplay);
        verify(seatCacheService, times(1)).syncReservedSeatsAfterCommit(any(), any(), any());

        request.setDepositorName("다른 입금자");
        assertThatThrownBy(() -> virtualAccountPayments.completeDepositFromGateway(request, payment.getReservation().getUserId()))
                .hasMessage("이미 처리된 입금 정보와 요청이 일치하지 않습니다.");
        assertThat(payment.getHistories()).hasSize(historiesBeforeReplay);
    }

    @Test
    void localExpirationQueryExcludesWaitingBankTransfer() {
        Payment payment = payment("bank-awaiting-notification");
        payment.switchToBankTransfer();
        payment.waitDeposit(BankCompany.KB.getBankName(), "1111-123456", LocalDateTime.now().minusDays(1));
        payments.saveAndFlush(payment);
        assertThat(payments.findExpiredPaymentIds(List.of(PaymentStatus.READY, PaymentStatus.WAITING_DEPOSIT),
                PaymentMethod.BANK_TRANSFER, PaymentStatus.WAITING_DEPOSIT, LocalDateTime.now(), PageRequest.of(0, 100)
        )).doesNotContain(payment.getPaymentId());
    }

    @Test
    void completedRefundAllowsNextPartialRefund() {
        Payment payment = payments.saveAndFlush(payment("next-partial-refund"));
        PaymentRefundProcess first = processes.saveAndFlush(PaymentRefundProcess.create(
                payment, payment.getReservation().getTickets(), 3000, 0, false, null
        ));
        first.gatewayFailed("응답 유실");
        processes.flush();
        first.gatewayRetryRequested();
        first.gatewaySucceeded();
        first.localFailed("로컬 반영 실패");
        processes.flush();
        first.localSucceeded();
        processes.flush();

        PaymentRefundProcess second = processes.saveAndFlush(PaymentRefundProcess.create(
                payment, payment.getReservation().getTickets(), 2000, 0, false, null
        ));
        assertThat(first.getStatus()).isEqualTo(PaymentRefundProcessStatus.LOCAL_SUCCEEDED);
        assertThat(second.getPaymentRefundProcessId()).isNotEqualTo(first.getPaymentRefundProcessId());
        assertThat(second.getRefundAmount()).isEqualTo(2000);
    }

    @Test
    void paymentSnapshotStaysFixedWhileProcessAndHistoryTrackChanges() {
        Payment payment = payment("snapshot");
        payment.approvalRequested();
        payment.completeCard("TX-1", CardCompany.SHINHAN, "****1111", LocalDateTime.now());
        payment.partialRefund(3000);
        payments.saveAndFlush(payment);
        Long id = payment.getPaymentId();
        entityManager.clear();

        Payment loaded = payments.findById(id).orElseThrow();
        assertThat(loaded.getAmount()).isEqualTo(12000);
        assertThat(loaded.getSnapshot().getReservationFeeAmount()).isEqualTo(4000);
        assertThat(loaded.getSnapshot().getDiscounts()).hasSize(1);
        assertThat(loaded.getSnapshot().getDiscounts().getFirst().getDiscountAmount())
                .isEqualTo(2000);
        assertThat(loaded.getSnapshot().getTickets().getFirst().getTicketPrice()).isEqualTo(10000);
        assertThat(loaded.getRefundedAmount()).isEqualTo(3000);
        assertThat(loaded.getProcess().getStage()).isEqualTo(PaymentProcessStage.LOCAL_COMPLETED);
        assertThat(loaded.getHistories())
                .extracting(PaymentHistory::getEventType)
                .containsExactly(
                        PaymentHistoryType.CREATED,
                        PaymentHistoryType.APPROVAL_REQUESTED,
                        PaymentHistoryType.GATEWAY_APPROVED,
                        PaymentHistoryType.COMPLETED,
                        PaymentHistoryType.REFUNDED);
        assertThatThrownBy(() -> loaded.getSnapshot().getDiscounts().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void failureAndRetryHistorySurvivesWhileLatestStateChanges() {
        Payment payment = payments.saveAndFlush(payment("retry"));
        payment.complete(LocalDateTime.now());
        PaymentRefundProcess process = PaymentRefundProcess.create(
                payment,
                payment.getReservation().getTickets(),
                5000,
                1000,
                false,
                account()
        );
        processes.saveAndFlush(process);
        Long id = process.getPaymentRefundProcessId();
        process.gatewayFailed("응답 유실");
        process.validateRefundAccount(account());
        process.gatewayRetryRequested();
        process.gatewaySucceeded();
        processes.flush();
        entityManager.clear();

        PaymentRefundProcess loaded = processes.findById(id).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(PaymentRefundProcessStatus.GATEWAY_SUCCEEDED);
        assertThat(loaded.getRetryCount()).isEqualTo(1);
        assertThat(loaded.getRefund().getSnapshot().getRefundAmount()).isEqualTo(5000);
        assertThat(loaded.getHistories())
                .extracting(PaymentRefundHistory::getEventType)
                .containsExactly(
                        PaymentRefundHistoryType.REQUESTED,
                        PaymentRefundHistoryType.GATEWAY_FAILED,
                        PaymentRefundHistoryType.RETRY_REQUESTED,
                        PaymentRefundHistoryType.GATEWAY_SUCCEEDED);
        assertThat(loaded.getHistories().get(1).getFailureReason()).isEqualTo("응답 유실");
        assertThat(
                        processes
                                .findFirstByReservationAndStatusInOrderByPaymentRefundProcessIdDesc(
                                        loaded.getReservation(),
                                        List.of(PaymentRefundProcessStatus.GATEWAY_SUCCEEDED)))
                .map(PaymentRefundProcess::getPaymentRefundProcessId)
                .contains(id);
        assertThat(refundHistories.existsByPaymentRefundProcess(loaded, PaymentRefundHistoryType.LOCAL_APPLIED)).isFalse();
    }

    @Test
    void successfulRefundResponseUsesSnapshotAndDoesNotIncludeFailureEvents() {
        Payment payment = payments.saveAndFlush(payment("history"));
        payment.complete(LocalDateTime.now());
        PaymentRefundProcess process = processes.saveAndFlush(
                PaymentRefundProcess.create(
                        payment,
                        payment.getReservation().getTickets(),
                        5000,
                        1000,
                        false,
                        null
                )
        );
        process.gatewayFailed("일시적 실패");
        process.gatewayRetryRequested();
        process.gatewaySucceeded();
        payment.applyCancellation(5000, 1000);
        refundHistories.save(PaymentRefundHistory.create(process, payment));
        process.localSucceeded();
        processes.flush();
        Long id = process.getPaymentRefundProcessId();
        Reservation reservation = process.getReservation();
        entityManager.clear();

        List<PaymentRefundHistory> successful = refundHistories.findByReservationOrderByPaymentRefundHistoryIdDesc(reservation, PaymentRefundHistoryType.LOCAL_APPLIED);
        assertThat(successful).hasSize(1);
        assertThat(successful.getFirst().toResponse().getRefundAmount()).isEqualTo(5000);
        assertThat(successful.getFirst().toResponse().getTickets().getFirst().getTicketPrice())
                .isEqualTo(10000);
        assertThat(successful.getFirst().toResponse().getTickets().getFirst().getPaymentRefundSnapshotTicketId())
                .isNotNull();
        assertThat(successful.getFirst().getRefundedAmountAfter()).isEqualTo(5000);
        PaymentRefundProcess loaded = processes.findById(id).orElseThrow();
        assertThat(loaded.getRefund().getStatus()).isEqualTo(PaymentRefundStatus.COMPLETED);
        assertThat(loaded.getHistories()).hasSize(6);
        assertThat(refundHistories.existsByPaymentRefundProcess(loaded, PaymentRefundHistoryType.LOCAL_APPLIED)).isTrue();
    }

    @Test
    void retryCannotChangeFrozenAccountEvenWhenMaskedSuffixIsTheSame() {
        Payment payment = payments.saveAndFlush(payment("account"));
        PaymentRefundProcess process = processes.saveAndFlush(
                PaymentRefundProcess.create(
                        payment,
                        payment.getReservation().getTickets(),
                        5000,
                        false,
                        account()
                )
        );
        RefundAccountRequest changed = RefundAccountRequest.builder()
                .bankCompany(BankCompany.KB)
                .accountNumber("9999567890")
                .accountHolder("사용자")
                .build();
        assertThatThrownBy(() -> process.validateRefundAccount(changed))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(process.getRefund().getSnapshot().getRefundAccountNumberHash()).hasSize(64);
        assertThat(process.getRefundAccountNumberMasked()).isEqualTo("******7890");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rolledBackPaymentCreatesNoSnapshotProcessOrHistory() {
        long before = payments.count();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(
                        () ->
                                tx.executeWithoutResult(
                                        status -> {
                                            payments.saveAndFlush(payment("rollback"));
                                            throw new IllegalStateException("롤백");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(payments.count()).isEqualTo(before);
        assertThat(paymentHistories.count()).isZero();
        assertThat(
                        entityManager
                                .createQuery("select count(s) from PaymentSnapshot s", Long.class)
                                .getSingleResult())
                .isZero();
        assertThat(
                        entityManager
                                .createQuery("select count(p) from PaymentProcess p", Long.class)
                                .getSingleResult())
                .isZero();
    }

    @Test
    void standardCardNotificationCannotReviveACancelledPayment() {
        Payment payment = payment("cancelled-standard-card");
        payment.cancel();
        payments.saveAndFlush(payment);
        String pgId = "20261109550e8400-e29b-41d4-a716-446655440001";
        payment.bindGatewayPayment(pgId);
        payments.flush();
        CardPaymentNotification event = new CardPaymentNotification(1, pgId + ":APPROVE:INITIAL", 1L, pgId,
                payment.getPaymentNo(), "user", PaymentMethod.CREDIT_CARD, PaymentStatus.PAID,
                new BigDecimal("12000"), "CARD-CANCELLED", CardCompany.SHINHAN, "4111-****-****-1111",
                LocalDateTime.now());
        long historyCount = paymentHistories.count();
        assertThat(cardNotifications.receive(event).outcome()).isEqualTo(CardPaymentNotificationResponse.Outcome.REJECTED);
        payments.flush();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(paymentHistories.count()).isEqualTo(historyCount);
    }

    private Payment payment(String key) {
        Event event = events.save(
                Event.builder()
                        .artistName("공연자")
                        .title("공연")
                        .venue("공연장")
                        .eventDateTime(LocalDateTime.now().plusMonths(1))
                        .totalSeats(10)
                        .availableSeats(10)
                        .status(EventStatus.ON_SALE)
                        .maxTicketsPerPerson(4)
                        .build()
        );
        Reservation reservation = reservations.save(
                Reservation.builder()
                        .orderId(key)
                        .userId("user")
                        .event(event)
                        .status(ReservationStatus.PENDING_PAYMENT)
                        .reservedAt(LocalDateTime.now())
                        .build()
        );
        Seat seat = seats.save(
                Seat.builder()
                        .event(event)
                        .zone("A")
                        .seatRow(1)
                        .seatCol(1)
                        .grade(SeatGrade.VIP)
                        .price(10000)
                        .status(SeatStatus.LOCKED)
                        .build()
        );
        Ticket ticket = tickets.save(
                Ticket.builder()
                        .reservation(reservation)
                        .event(event)
                        .seat(seat)
                        .userId("user")
                        .build()
        );
        ReservationDiscount discount = ReservationDiscount.builder()
                .reservation(reservation)
                .discountType(DiscountType.COUPON)
                .discountName("할인")
                .discountAmount(2000)
                .build();
        return Payment.builder()
                .reservation(reservation)
                .paymentNo("PAY-" + key)
                .idempotencyKey(key)
                .method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.READY)
                .requestedAt(LocalDateTime.now())
                .snapshot(
                        PaymentSnapshot.builder()
                                .amount(12000)
                                .reservationFeeAmount(4000)
                                .deliveryFeeAmount(0)
                                .tickets(List.of(ticket))
                                .discounts(List.of(discount))
                                .build()
                )
                .build();
    }

    private RefundAccountRequest account() {
        return RefundAccountRequest.builder()
                .bankCompany(BankCompany.KB)
                .accountNumber("1234567890")
                .accountHolder("사용자")
                .build();
    }
}
