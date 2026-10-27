package dev.bum.ticket_service.service.payment.refund;

import dev.bum.common.service.ticket.payment.dto.GatewayRefundResolutionResponse.Outcome;
import dev.bum.common.service.ticket.payment.dto.GatewayRefundResolutionResponse;
import dev.bum.common.service.ticket.payment.dto.RefundAccountRequest;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.CardCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.audit.AuditContext;
import dev.bum.ticket_service.audit.AuditLog;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayRefundClient;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.payment.card.CardPaymentInfo;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefund;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundHistory;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundHistoryJpaRepository;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcess;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcessJpaRepository;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcessStatus;
import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundSnapshot;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationJpaRepository;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.coupon.userCoupon.UserCouponRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketJpaRepository;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import dev.bum.ticket_service.service.ticket.TicketPurchaseCountService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PaymentRefundProcessServiceTest {

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    @Mock
    private PaymentGatewayRefundClient paymentGatewayRefundClient;

    @Mock
    private TicketPurchaseCountService ticketPurchaseCountService;

    @InjectMocks
    private PaymentRefundProcessService paymentRefundProcessService;

    @Spy
    @InjectMocks
    private PaymentRefundProcessCreator creator;

    @Mock
    private PaymentRefundRollbackRecorder rollbackRecorder;

    @Mock
    private PaymentRefundProcessJpaRepository paymentRefundProcessJpaRepository;

    @Mock
    private PaymentRefundHistoryJpaRepository paymentRefundHistoryJpaRepository;

    @Mock
    private CardPaymentRefundService cardPaymentRefundService;

    @Mock
    private VirtualAccountPaymentRefundService virtualAccountPaymentRefundService;

    @Mock
    private ReservationJpaRepository reservationJpaRepository;

    @Mock
    private TicketJpaRepository ticketJpaRepository;

    @Mock
    private ReservationDiscountJpaRepository reservationDiscountJpaRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private SeatCacheService seatCacheService;

    @BeforeEach
    void configureCreationAndLocks() {
        ReflectionTestUtils.setField(paymentRefundProcessService, "paymentRefundProcessCreator", creator);
        lenient().when(paymentRefundProcessJpaRepository.save(any(PaymentRefundProcess.class)))
                .thenAnswer(invocation -> {
                    PaymentRefundProcess created = invocation.getArgument(0);
                    ReflectionTestUtils.setField(created, "paymentRefundProcessId", 999L);
                    given(paymentRefundProcessJpaRepository.findById(999L)).willReturn(Optional.of(created));
                    return created;
                });
    }

    @AfterEach
    void tearDown() {
        AuditContext.clear();
    }

    @Test
    @DisplayName("관리자 로컬 수동 완료 메서드는 감사 로그 대상이다")
    void complete_local_has_audit_log() throws NoSuchMethodException {
        Method method = PaymentRefundProcessService.class.getMethod("completeLocal", Long.class);

        AuditLog auditLog = method.getAnnotation(AuditLog.class);

        assertThat(auditLog).isNotNull();
        assertThat(auditLog.action()).isEqualTo("PAYMENT_REFUND_PROCESS_LOCAL_COMPLETE");
        assertThat(auditLog.targetType()).isEqualTo("PAYMENT_REFUND_PROCESS");
    }

    @Test
    void sameTicketSetReusesSnapshotAmountsAfterCutoffWithoutRecalculation() {
        Reservation reservation = reservation(ReservationStatus.PAID);
        Payment payment = cardPayment(reservation);
        Ticket first = ticket(1L, reservation, TicketStatus.PAID);
        Ticket second = ticket(2L, reservation, TicketStatus.PAID);
        LocalDateTime capturedAt = LocalDateTime.of(2026, 10, 14, 23, 49);
        PaymentRefundSnapshot snapshot = PaymentRefundSnapshot.builder()
                .payment(payment)
                .tickets(List.of(first, second))
                .refundAmount(230000)
                .cancellationFeeAmount(20000)
                .fullCancellation(true)
                .refundAccount(null)
                .capturedAt(capturedAt)
                .restoreCoupon(true)
                .build();
        PaymentRefundProcess previous = PaymentRefundProcess.builder().paymentRefundProcessId(1L)
                .refund(PaymentRefund.builder()
                        .payment(payment)
                        .snapshot(snapshot)
                        .build()).status(PaymentRefundProcessStatus.GATEWAY_FAILED).build();
        given(clock.instant()).willReturn(Instant.parse("2026-10-15T14:55:00Z"));
        given(paymentRefundProcessJpaRepository.findFirstByReservationAndStatusInOrderByPaymentRefundProcessIdDesc(any(), anyList()))
                .willReturn(Optional.of(previous));
        given(paymentRefundProcessJpaRepository.findById(previous.getPaymentRefundProcessId())).willReturn(Optional.of(previous));
        given(paymentGatewayRefundClient.resolve(any()))
                .willReturn(GatewayRefundResolutionResponse.builder()
                        .paymentRefundProcessId(1L)
                        .paymentNo("PAY-1")
                        .outcome(Outcome.NOT_FOUND)
                        .build());

        PaymentRefundProcessGatewayAttempt attempt = prepareAttempt(
                payment, List.of(second, first), true, null
        );

        assertThat(attempt.getPaymentRefundProcessId()).isEqualTo(1L);
        assertThat(attempt.getCalculation().refundAmount()).isEqualTo(230000);
        assertThat(attempt.getCalculation().cancellationFeeAmount()).isEqualTo(20000);
        assertThat(attempt.getCalculation().restoreCoupon()).isTrue();
        assertThat(snapshot.getCapturedAt()).isEqualTo(capturedAt);
        then(ticketJpaRepository).shouldHaveNoInteractions();
        then(reservationDiscountJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    void changedRequestCreatesNewSnapshotOnlyAfterPgClosesTheOldRequest() {
        Reservation reservation = reservation(ReservationStatus.PAID);
        Payment payment = cardPayment(reservation);
        Ticket first = ticket(1L, reservation, TicketStatus.PAID);
        Ticket second = ticket(2L, reservation, TicketStatus.PAID);
        PaymentRefundProcess previous = refundProcess(1L, payment, List.of(first), 100000, false,
                PaymentRefundProcessStatus.GATEWAY_FAILED);
        given(paymentRefundProcessJpaRepository.findFirstByReservationAndStatusInOrderByPaymentRefundProcessIdDesc(any(), anyList()))
                .willReturn(Optional.of(previous));
        given(paymentRefundProcessJpaRepository.findById(previous.getPaymentRefundProcessId())).willReturn(Optional.of(previous));
        given(paymentGatewayRefundClient.resolve(any()))
                .willReturn(GatewayRefundResolutionResponse.builder()
                        .paymentRefundProcessId(1L)
                        .paymentNo("PAY-1")
                        .outcome(Outcome.CLOSED)
                        .build());
        given(ticketJpaRepository.findByReservation(reservation)).willReturn(List.of(first, second));
        given(reservationDiscountJpaRepository.findByReservation(reservation)).willReturn(List.of());

        PaymentRefundProcessGatewayAttempt attempt = prepareAttempt(
                payment, List.of(second), false, null
        );

        assertThat(previous.getStatus()).isEqualTo(PaymentRefundProcessStatus.SUPERSEDED);
        assertThat(attempt.getCalculation().refundAmount()).isEqualTo(125000);
        ArgumentCaptor<PaymentRefundProcess> created = ArgumentCaptor.forClass(PaymentRefundProcess.class);
        then(paymentRefundProcessJpaRepository).should().save(created.capture());
        assertThat(created.getValue().getRefund().getSnapshot().getTickets().getFirst().getTicketId()).isEqualTo(2L);
        assertThat(created.getValue().getRefund().getSnapshot().getCapturedAt()).isEqualTo(LocalDateTime.now(clock));
        then(paymentGatewayRefundClient).should().resolve(argThat(request -> request.closeIfUnexecuted()));
    }

    @Test
    void changedRequestReturnsRecoveryPlanWhenPgReportsSuccess() {
        Reservation reservation = reservation(ReservationStatus.PAID);
        Payment payment = cardPayment(reservation);
        Ticket first = ticket(1L, reservation, TicketStatus.PAID);
        Ticket second = ticket(2L, reservation, TicketStatus.PAID);
        PaymentRefundProcess previous = refundProcess(1L, payment, List.of(first), 100000, false,
                PaymentRefundProcessStatus.GATEWAY_FAILED);
        given(paymentRefundProcessJpaRepository.findFirstByReservationAndStatusInOrderByPaymentRefundProcessIdDesc(any(), anyList()))
                .willReturn(Optional.of(previous));
        given(paymentRefundProcessJpaRepository.findById(previous.getPaymentRefundProcessId())).willReturn(Optional.of(previous));
        given(paymentGatewayRefundClient.resolve(any()))
                .willReturn(GatewayRefundResolutionResponse.builder()
                        .paymentRefundProcessId(1L)
                        .paymentNo("PAY-1")
                        .outcome(Outcome.SUCCEEDED)
                        .build());

        PaymentRefundProcessGatewayAttempt attempt = prepareAttempt(
                payment, List.of(second), false, null
        );
        assertThat(attempt.getAction()).isEqualTo(RefundAction.RECOVER_PREVIOUS_REQUEST);
        assertThat(previous.getStatus()).isEqualTo(PaymentRefundProcessStatus.GATEWAY_SUCCEEDED);
        then(paymentRefundProcessJpaRepository).should(never()).save(any());
    }

    @Test
    void changedRequestAfterCutoffDoesNotCloseTheOriginalRequest() {
        Reservation reservation = reservation(ReservationStatus.PAID);
        Payment payment = cardPayment(reservation);
        Ticket first = ticket(1L, reservation, TicketStatus.PAID);
        Ticket second = ticket(2L, reservation, TicketStatus.PAID);
        PaymentRefundProcess previous = refundProcess(1L, payment, List.of(first), 100000, false,
                PaymentRefundProcessStatus.GATEWAY_FAILED);
        given(clock.instant()).willReturn(Instant.parse("2026-10-15T14:55:00Z"));
        given(paymentRefundProcessJpaRepository.findFirstByReservationAndStatusInOrderByPaymentRefundProcessIdDesc(any(), anyList()))
                .willReturn(Optional.of(previous));
        given(paymentRefundProcessJpaRepository.findById(previous.getPaymentRefundProcessId())).willReturn(Optional.of(previous));

        assertThatThrownBy(() -> prepareAttempt(payment, List.of(second), false, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(previous.getStatus()).isEqualTo(PaymentRefundProcessStatus.GATEWAY_FAILED);
        then(paymentGatewayRefundClient).shouldHaveNoInteractions();
    }

    @Test
    void sameRequestClosedAtPgCanRecoverWithNewKeyAndOriginalTerms() {
        Reservation reservation = reservation(ReservationStatus.PAID);
        Payment payment = cardPayment(reservation);
        Ticket ticket = ticket(1L, reservation, TicketStatus.PAID);
        PaymentRefundProcess previous = refundProcess(1L, payment, List.of(ticket), 100000, false,
                PaymentRefundProcessStatus.GATEWAY_FAILED);
        LocalDateTime capturedAt = previous.getRefund().getSnapshot().getCapturedAt();
        given(clock.instant()).willReturn(Instant.parse("2026-10-15T14:55:00Z"));
        given(paymentRefundProcessJpaRepository.findFirstByReservationAndStatusInOrderByPaymentRefundProcessIdDesc(any(), anyList()))
                .willReturn(Optional.of(previous));
        given(paymentRefundProcessJpaRepository.findById(previous.getPaymentRefundProcessId())).willReturn(Optional.of(previous));
        given(paymentGatewayRefundClient.resolve(any()))
                .willReturn(GatewayRefundResolutionResponse.builder()
                        .paymentRefundProcessId(1L)
                        .paymentNo("PAY-1")
                        .outcome(Outcome.CLOSED)
                        .build());

        PaymentRefundProcessGatewayAttempt attempt = prepareAttempt(
                payment, List.of(ticket), false, null
        );

        assertThat(previous.getStatus()).isEqualTo(PaymentRefundProcessStatus.SUPERSEDED);
        assertThat(attempt.getCalculation().refundAmount()).isEqualTo(100000);
        ArgumentCaptor<PaymentRefundProcess> created = ArgumentCaptor.forClass(PaymentRefundProcess.class);
        then(paymentRefundProcessJpaRepository).should().save(created.capture());
        assertThat(created.getValue().getRefund().getSnapshot().getCapturedAt()).isEqualTo(capturedAt);
    }

    @ParameterizedTest
    @EnumSource(value = Outcome.class, names = {"NOT_FOUND", "REQUESTED", "FAILED"})
    @DisplayName("GATEWAY_FAILED 환불 프로세스는 사용자 재요청 시 REQUESTED 상태로 gateway 재시도 대상이 된다")
    void start_gateway_attempt_when_gateway_failed(Outcome outcome) {
        Reservation reservation = reservation(ReservationStatus.PAID);
        Payment payment = virtualAccountPayment(reservation);
        Ticket ticket = ticket(1L, reservation, TicketStatus.PAID);
        RefundAccountRequest refundAccount = RefundAccountRequest.builder()
                .bankCompany(BankCompany.SHINHAN)
                .accountNumber("9876543210")
                .accountHolder("김환불")
                .build();
        PaymentRefundProcess process = PaymentRefundProcess.builder()
                .paymentRefundProcessId(1L)
                .refund(PaymentRefund.builder()
                        .payment(payment)
                        .snapshot(
                                PaymentRefundSnapshot.builder()
                                        .payment(payment)
                                        .tickets(List.of(ticket))
                                        .refundAmount(100000)
                                        .cancellationFeeAmount(0)
                                        .fullCancellation(false)
                                        .refundAccount(refundAccount)
                                        .capturedAt(LocalDateTime.now())
                                        .restoreCoupon(false)
                                        .build()
                        )
                        .build())
                .status(PaymentRefundProcessStatus.GATEWAY_FAILED)
                .build();

        given(paymentRefundProcessJpaRepository.findFirstByReservationAndStatusInOrderByPaymentRefundProcessIdDesc(any(), anyList()))
                .willReturn(Optional.of(process));
        given(paymentRefundProcessJpaRepository.findById(process.getPaymentRefundProcessId())).willReturn(Optional.of(process));

        given(paymentGatewayRefundClient.resolve(any())).willReturn(
                GatewayRefundResolutionResponse.builder()
                        .paymentRefundProcessId(1L)
                        .paymentNo("PAY-1")
                        .outcome(outcome)
                        .build()
        );
        PaymentRefundProcessGatewayAttempt attempt = prepareAttempt(
                payment,
                List.of(ticket),
                false,
                refundAccount
        );

        assertThat(attempt.getAction()).isEqualTo(RefundAction.REQUEST_GATEWAY);
        assertThat(process.getStatus()).isEqualTo(PaymentRefundProcessStatus.REQUESTED);
        assertThat(process.getRefundBankCompany()).isEqualTo(BankCompany.SHINHAN);
        assertThat(process.getRefundAccountNumberMasked()).isEqualTo("******3210");
        assertThat(process.getRefundAccountHolder()).isEqualTo("김환불");
    }

    @Test
    @DisplayName("GATEWAY_SUCCEEDED 환불 프로세스는 사용자 재요청 시 gateway 재호출 없이 로컬 결제 반영 대상이 된다")
    void start_gateway_attempt_when_gateway_succeeded() {
        Reservation reservation = reservation(ReservationStatus.PAID);
        Payment payment = cardPayment(reservation);
        Ticket ticket = ticket(1L, reservation, TicketStatus.PAID);
        PaymentRefundProcess process = refundProcess(1L, payment, List.of(ticket), 100000, false, PaymentRefundProcessStatus.GATEWAY_SUCCEEDED);

        given(paymentRefundProcessJpaRepository.findFirstByReservationAndStatusInOrderByPaymentRefundProcessIdDesc(any(), anyList()))
                .willReturn(Optional.of(process));
        given(paymentRefundProcessJpaRepository.findById(process.getPaymentRefundProcessId())).willReturn(Optional.of(process));

        PaymentRefundProcessGatewayAttempt attempt = prepareAttempt(
                payment,
                List.of(ticket),
                false,
                null
        );

        assertThat(attempt.getAction()).isEqualTo(RefundAction.APPLY_LOCAL);
        assertThat(process.getStatus()).isEqualTo(PaymentRefundProcessStatus.GATEWAY_SUCCEEDED);
    }

    @Test
    @DisplayName("관리자는 GATEWAY_SUCCEEDED 환불 프로세스의 로컬 상태를 수동 완료 처리한다")
    void complete_local_when_gateway_succeeded() {
        Reservation reservation = reservation(ReservationStatus.PAID);
        Payment payment = cardPayment(reservation);
        Ticket firstTicket = ticket(1L, reservation, TicketStatus.PAID);
        Ticket secondTicket = ticket(2L, reservation, TicketStatus.PAID);
        PaymentRefundProcess process = refundProcess(1L, payment, List.of(firstTicket, secondTicket), 250000, true, PaymentRefundProcessStatus.GATEWAY_SUCCEEDED);

        given(paymentRefundProcessJpaRepository.findReservationIdById(1L)).willReturn(Optional.of(1L));
        given(reservationJpaRepository.findByIdForUpdateNowait(1L)).willReturn(Optional.of(reservation));
        given(paymentRefundProcessJpaRepository.findById(1L)).willReturn(Optional.of(process));
        given(ticketJpaRepository.findAllByTicketIdIn(List.of(1L, 2L))).willReturn(List.of(firstTicket, secondTicket));

        paymentRefundProcessService.completeLocal(1L);

        ArgumentCaptor<PaymentRefundHistory> historyCaptor = ArgumentCaptor.forClass(PaymentRefundHistory.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(250000);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(firstTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(secondTicket.getSeat().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(process.getStatus()).isEqualTo(PaymentRefundProcessStatus.LOCAL_SUCCEEDED);
        assertThat(AuditContext.getBeforeData()).containsEntry("status", "GATEWAY_SUCCEEDED");
        assertThat(AuditContext.getAfterData()).containsEntry("status", "LOCAL_SUCCEEDED");
        then(paymentRefundHistoryJpaRepository).should().save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getRefundAmount()).isEqualTo(250000);
        assertThat(historyCaptor.getValue().getPaymentRefundProcess()).isEqualTo(process);
    }

    @Test
    @DisplayName("전체 예매 취소의 환불액과 수수료를 합산해 결제 정산을 완료한다")
    void complete_local_full_cancellation_with_fee() {
        Reservation reservation = reservation(ReservationStatus.PAID);
        Payment payment = cardPayment(reservation);
        Ticket firstTicket = ticket(1L, reservation, TicketStatus.PAID);
        Ticket secondTicket = ticket(2L, reservation, TicketStatus.PAID);
        PaymentRefundProcess process = refundProcess(
                1L, payment, List.of(firstTicket, secondTicket), 190000, 60000, true,
                PaymentRefundProcessStatus.GATEWAY_SUCCEEDED);

        given(paymentRefundProcessJpaRepository.findReservationIdById(1L)).willReturn(Optional.of(1L));
        given(reservationJpaRepository.findByIdForUpdateNowait(1L)).willReturn(Optional.of(reservation));
        given(paymentRefundProcessJpaRepository.findById(1L)).willReturn(Optional.of(process));
        given(ticketJpaRepository.findAllByTicketIdIn(List.of(1L, 2L))).willReturn(List.of(firstTicket, secondTicket));

        paymentRefundProcessService.completeLocal(1L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(190000);
        assertThat(payment.getCancellationFeeAmount()).isEqualTo(60000);
        assertThat(payment.getRefundableAmount()).isZero();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    @DisplayName("관리자는 LOCAL_FAILED 환불 프로세스의 로컬 상태를 수동 완료 처리한다")
    void complete_local_when_local_failed() {
        Reservation reservation = reservation(ReservationStatus.PAID);
        Payment payment = virtualAccountPayment(reservation);
        Ticket ticket = ticket(1L, reservation, TicketStatus.PAID);
        PaymentRefundProcess process = refundProcess(1L, payment, List.of(ticket), 100000, false, PaymentRefundProcessStatus.LOCAL_FAILED);

        given(paymentRefundProcessJpaRepository.findReservationIdById(1L)).willReturn(Optional.of(1L));
        given(reservationJpaRepository.findByIdForUpdateNowait(1L)).willReturn(Optional.of(reservation));
        given(paymentRefundProcessJpaRepository.findById(1L)).willReturn(Optional.of(process));
        given(ticketJpaRepository.findAllByTicketIdIn(List.of(1L))).willReturn(List.of(ticket));

        paymentRefundProcessService.completeLocal(1L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(100000);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PARTIALLY_CANCELLED);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(process.getStatus()).isEqualTo(PaymentRefundProcessStatus.LOCAL_SUCCEEDED);
        assertThat(AuditContext.getBeforeData()).containsEntry("status", "LOCAL_FAILED");
        assertThat(AuditContext.getAfterData()).containsEntry("status", "LOCAL_SUCCEEDED");
        then(paymentRefundHistoryJpaRepository).should().save(any(PaymentRefundHistory.class));
    }

    @Test
    void manualCompletionRejectsCancelledTicketBeforeApplyingPaymentRefund() {
        Reservation reservation = reservation(ReservationStatus.PAID);
        Payment payment = cardPayment(reservation);
        Ticket ticket = ticket(1L, reservation, TicketStatus.CANCELLED);
        PaymentRefundProcess process = refundProcess(
                1L, payment, List.of(ticket), 100000, false, PaymentRefundProcessStatus.GATEWAY_SUCCEEDED
        );
        given(paymentRefundProcessJpaRepository.findReservationIdById(1L)).willReturn(Optional.of(1L));
        given(reservationJpaRepository.findByIdForUpdateNowait(1L)).willReturn(Optional.of(reservation));
        given(paymentRefundProcessJpaRepository.findById(1L)).willReturn(Optional.of(process));
        given(ticketJpaRepository.findAllByTicketIdIn(List.of(1L))).willReturn(List.of(ticket));

        assertThatThrownBy(() -> paymentRefundProcessService.completeLocal(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 취소되었거나 만료된 티켓은 취소할 수 없습니다.");

        assertThat(payment.getRefundedAmount()).isZero();
        then(ticketPurchaseCountService).shouldHaveNoInteractions();
        then(paymentRefundHistoryJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이미 환불 이력이 저장된 환불 프로세스는 중복 이력 저장을 차단한다")
    void prevent_duplicate_refund_history() {
        Reservation reservation = reservation(ReservationStatus.PAID);
        Payment payment = cardPayment(reservation);
        Ticket ticket = ticket(1L, reservation, TicketStatus.PAID);
        PaymentRefundProcess process = refundProcess(1L, payment, List.of(ticket), 100000, false, PaymentRefundProcessStatus.GATEWAY_SUCCEEDED);

        given(paymentRefundProcessJpaRepository.findReservationIdById(1L)).willReturn(Optional.of(1L));
        given(reservationJpaRepository.findByIdForUpdateNowait(1L)).willReturn(Optional.of(reservation));
        given(paymentRefundProcessJpaRepository.findById(1L)).willReturn(Optional.of(process));
        given(ticketJpaRepository.findAllByTicketIdIn(List.of(1L))).willReturn(List.of(ticket));
        given(paymentRefundHistoryJpaRepository.existsByPaymentRefundProcess(process)).willReturn(true);

        assertThatThrownBy(() -> paymentRefundProcessService.completeLocal(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 환불 이력이 저장된 환불 처리입니다.");

        then(paymentRefundHistoryJpaRepository).should().existsByPaymentRefundProcess(process);
        then(paymentRefundHistoryJpaRepository).shouldHaveNoMoreInteractions();
        assertThat(process.getStatus()).isEqualTo(PaymentRefundProcessStatus.LOCAL_FAILED);
    }

    private PaymentRefundProcess refundProcess(
            Long paymentRefundProcessId,
            Payment payment,
            List<Ticket> tickets,
            Integer refundAmount,
            boolean fullCancellation,
            PaymentRefundProcessStatus status
    ) {
        return refundProcess(paymentRefundProcessId, payment, tickets, refundAmount, 0, fullCancellation, status);
    }

    private PaymentRefundProcess refundProcess(
            Long paymentRefundProcessId,
            Payment payment,
            List<Ticket> tickets,
            Integer refundAmount,
            Integer cancellationFeeAmount,
            boolean fullCancellation,
            PaymentRefundProcessStatus status
    ) {
        return PaymentRefundProcess.builder()
                .paymentRefundProcessId(paymentRefundProcessId)
                .payment(payment)
                .refundAmount(refundAmount)
                .cancellationFeeAmount(cancellationFeeAmount)
                .fullCancellation(fullCancellation)
                .selectedTicketIds(tickets.stream()
                        .map(Ticket::getTicketId)
                        .map(String::valueOf)
                        .reduce((left, right) -> left + "," + right)
                        .orElse(""))
                .status(status)
                .retryCount(0)
                .lastTriedAt(LocalDateTime.now())
                .build();
    }

    private Payment cardPayment(Reservation reservation) {
        return Payment.builder()
                .paymentId(1L)
                .reservation(reservation)
                .paymentNo("PAY-1")
                .method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.PAID)
                .amount(250000)
                .refundedAmount(0)
                .cardInfo(CardPaymentInfo.builder()
                        .transactionId("CARD-1")
                        .cardCompany(CardCompany.SHINHAN)
                        .maskedCardNumber("4111-****-****-1111")
                        .build())
                .requestedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();
    }

    private Payment virtualAccountPayment(Reservation reservation) {
        return Payment.builder()
                .paymentId(1L)
                .reservation(reservation)
                .paymentNo("PAY-1")
                .method(PaymentMethod.BANK_TRANSFER)
                .status(PaymentStatus.PAID)
                .amount(250000)
                .refundedAmount(0)
                .requestedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();
    }

    private PaymentRefundProcessGatewayAttempt prepareAttempt(
            Payment payment, List<Ticket> selectedTickets, boolean fullCancellation, RefundAccountRequest account) {
        PaymentRefundRequestContext context = paymentRefundProcessService.inspectRequest(
                payment, selectedTickets, fullCancellation, account
        );
        return paymentRefundProcessService.startGatewayAttempt(
                payment, selectedTickets, fullCancellation, account, context
        );
    }

    private Reservation reservation(ReservationStatus status) {
        return Reservation.builder()
                .reservationId(1L)
                .orderId("ORDER-1")
                .userId("user01")
                .event(event())
                .status(status)
                .reservedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();
    }

    private Ticket ticket(Long ticketId, Reservation reservation, TicketStatus status) {
        return Ticket.builder()
                .ticketId(ticketId)
                .userId("user01")
                .reservation(reservation)
                .event(reservation.getEvent())
                .seat(seat(ticketId, reservation.getEvent()))
                .status(status)
                .build();
    }

    private Event event() {
        return Event.builder()
                .eventId(1L)
                .title("IU Concert")
                .venue("KSPO Dome")
                .eventDateTime(LocalDateTime.of(2099, 9, 18, 18, 0))
                .build();
    }

    private Seat seat(Long seatId, Event event) {
        return Seat.builder()
                .seatId(seatId)
                .event(event)
                .zone("VIP")
                .seatRow(1)
                .seatCol(seatId.intValue())
                .grade(SeatGrade.VIP)
                .price(125000)
                .status(SeatStatus.RESERVED)
                .build();
    }
}
