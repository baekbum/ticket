package dev.bum.ticket_service.service;

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
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.payment.CardPaymentInfo;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentRefundHistory;
import dev.bum.ticket_service.jpa.payment.PaymentRefundHistoryJpaRepository;
import dev.bum.ticket_service.jpa.payment.PaymentRefundProcess;
import dev.bum.ticket_service.jpa.payment.PaymentRefundProcessJpaRepository;
import dev.bum.ticket_service.jpa.payment.PaymentRefundProcessStatus;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscountJpaRepository;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketJpaRepository;
import dev.bum.ticket_service.service.payment.CardPaymentRefundService;
import dev.bum.ticket_service.service.payment.PaymentRefundProcessGatewayAttempt;
import dev.bum.ticket_service.service.payment.PaymentRefundProcessService;
import dev.bum.ticket_service.service.payment.VirtualAccountPaymentRefundService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PaymentRefundProcessServiceTest {

    @InjectMocks
    private PaymentRefundProcessService paymentRefundProcessService;

    @Mock
    private PaymentRefundProcessJpaRepository paymentRefundProcessJpaRepository;

    @Mock
    private PaymentRefundHistoryJpaRepository paymentRefundHistoryJpaRepository;

    @Mock
    private CardPaymentRefundService cardPaymentRefundService;

    @Mock
    private VirtualAccountPaymentRefundService virtualAccountPaymentRefundService;

    @Mock
    private TicketJpaRepository ticketJpaRepository;

    @Mock
    private ReservationDiscountJpaRepository reservationDiscountJpaRepository;

    @Mock
    private SeatCacheService seatCacheService;

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
    @DisplayName("GATEWAY_FAILED 환불 프로세스는 사용자 재요청 시 REQUESTED 상태로 gateway 재시도 대상이 된다")
    void start_gateway_attempt_when_gateway_failed() {
        Reservation reservation = reservation(ReservationStatus.PAID);
        Payment payment = virtualAccountPayment(reservation);
        Ticket ticket = ticket(1L, reservation, TicketStatus.PAID);
        PaymentRefundProcess process = refundProcess(1L, payment, List.of(ticket), 100000, false, PaymentRefundProcessStatus.GATEWAY_FAILED);
        RefundAccountRequest refundAccount = RefundAccountRequest.builder()
                .bankCompany(BankCompany.SHINHAN)
                .accountNumber("9876543210")
                .accountHolder("김환불")
                .build();

        given(paymentRefundProcessJpaRepository.findFirstByReservationAndStatusInOrderByPaymentRefundProcessIdDesc(any(), anyList()))
                .willReturn(Optional.of(process));

        PaymentRefundProcessGatewayAttempt attempt = paymentRefundProcessService.startGatewayAttempt(
                payment,
                List.of(ticket),
                100000,
                false,
                refundAccount
        );

        assertThat(attempt.isGatewayRequired()).isTrue();
        assertThat(attempt.isLocalPaymentRefundRequired()).isFalse();
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

        PaymentRefundProcessGatewayAttempt attempt = paymentRefundProcessService.startGatewayAttempt(
                payment,
                List.of(ticket),
                100000,
                false,
                null
        );

        assertThat(attempt.isGatewayRequired()).isFalse();
        assertThat(attempt.isLocalPaymentRefundRequired()).isTrue();
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
    @DisplayName("관리자는 LOCAL_FAILED 환불 프로세스의 로컬 상태를 수동 완료 처리한다")
    void complete_local_when_local_failed() {
        Reservation reservation = reservation(ReservationStatus.PAID);
        Payment payment = virtualAccountPayment(reservation);
        Ticket ticket = ticket(1L, reservation, TicketStatus.PAID);
        PaymentRefundProcess process = refundProcess(1L, payment, List.of(ticket), 100000, false, PaymentRefundProcessStatus.LOCAL_FAILED);

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
    @DisplayName("이미 환불 이력이 저장된 환불 프로세스는 중복 이력 저장을 차단한다")
    void prevent_duplicate_refund_history() {
        Reservation reservation = reservation(ReservationStatus.PAID);
        Payment payment = cardPayment(reservation);
        Ticket ticket = ticket(1L, reservation, TicketStatus.PAID);
        PaymentRefundProcess process = refundProcess(1L, payment, List.of(ticket), 100000, false, PaymentRefundProcessStatus.GATEWAY_SUCCEEDED);

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
        return PaymentRefundProcess.builder()
                .paymentRefundProcessId(paymentRefundProcessId)
                .payment(payment)
                .reservation(payment.getReservation())
                .paymentNo(payment.getPaymentNo())
                .method(payment.getMethod())
                .refundAmount(refundAmount)
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
                .eventDateTime(LocalDateTime.of(2026, 9, 18, 18, 0))
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
