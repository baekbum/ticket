package dev.bum.ticket_service.jpa.payment;

import dev.bum.common.service.ticket.coupon.coupon.enums.DiscountType;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.payment.dto.GatewayRefundResolutionRequest;
import dev.bum.common.service.ticket.payment.dto.GatewayRefundResolutionResponse.Outcome;
import dev.bum.common.service.ticket.payment.dto.GatewayRefundResolutionResponse;
import dev.bum.common.service.ticket.payment.dto.RefundAccountRequest;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayRefundClient;
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
import dev.bum.ticket_service.service.payment.refund.PaymentRefundProcessCreator;
import dev.bum.ticket_service.service.payment.refund.PaymentRefundRollbackRecorder;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import dev.bum.ticket_service.service.ticket.TicketPurchaseCountService;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@DataJpaTest
@ActiveProfiles("test")
@Import({PaymentRefundProcessService.class, PaymentRefundProcessCreator.class, PaymentRefundRollbackRecorder.class})
class PaymentModelIntegrationTest {
    @MockitoBean Clock clock;
    @MockitoBean PaymentGatewayRefundClient refundResolution;

    @BeforeEach
    void configureTimeAndGatewayResolution() {
        given(clock.instant()).willReturn(Instant.parse("2026-10-15T03:00:00Z"));
        given(clock.getZone()).willReturn(ZoneId.of("Asia/Seoul"));
        given(refundResolution.resolve(any())).willAnswer(invocation -> {
            GatewayRefundResolutionRequest request = invocation.getArgument(0);
            return GatewayRefundResolutionResponse.builder()
                    .paymentRefundProcessId(request.paymentRefundProcessId())
                    .paymentNo(request.paymentNo())
                    .outcome(Outcome.NOT_FOUND)
                    .build();
        });
    }

    @Autowired PaymentRefundProcessService processService;
    @MockitoBean SeatCacheService seatCacheService;

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
        assertThat(refundHistories.existsByPaymentRefundProcess(loaded)).isFalse();
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
        processService.savePaymentRefundHistory(
                process.getPaymentRefundProcessId(),
                payment,
                payment.getReservation().getTickets(),
                5000,
                false);
        TransactionSynchronizationUtils.triggerBeforeCommit(
                false);
        processes.flush();
        Long id = process.getPaymentRefundProcessId();
        Reservation reservation = process.getReservation();
        entityManager.clear();

        List<PaymentRefundHistory> successful = refundHistories.findByReservationOrderByPaymentRefundHistoryIdDesc(reservation);
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
        assertThat(refundHistories.existsByPaymentRefundProcess(loaded)).isTrue();
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
