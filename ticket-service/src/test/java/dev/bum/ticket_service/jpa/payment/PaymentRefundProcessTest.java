package dev.bum.ticket_service.jpa.payment;

import dev.bum.common.service.ticket.payment.dto.RefundAccountRequest;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentRefundProcessTest {

    @Test
    @DisplayName("무통장 환불 프로세스는 환불 계좌 정보를 마스킹해서 저장한다")
    void payment_refund_process_create_with_refund_account() {
        Reservation reservation = reservation();
        Payment payment = payment(reservation, PaymentMethod.BANK_TRANSFER);
        Ticket ticket = ticket(1L, reservation);

        PaymentRefundProcess process = PaymentRefundProcess.create(
                payment,
                List.of(ticket),
                100000,
                true,
                RefundAccountRequest.builder()
                        .bankCompany(BankCompany.KB)
                        .accountNumber("1234567890")
                        .accountHolder("홍길동")
                        .build()
        );

        assertThat(process.getStatus()).isEqualTo(PaymentRefundProcessStatus.REQUESTED);
        assertThat(process.getRefundBankCompany()).isEqualTo(BankCompany.KB);
        assertThat(process.getRefundAccountNumberMasked()).isEqualTo("******7890");
        assertThat(process.getRefundAccountHolder()).isEqualTo("홍길동");
        assertThat(process.getSelectedTicketIds()).isEqualTo("1");
    }

    @Test
    @DisplayName("환불 프로세스는 gateway 실패 상태를 기록한다")
    void payment_refund_process_gateway_failed() {
        Reservation reservation = reservation();
        PaymentRefundProcess process = PaymentRefundProcess.create(
                payment(reservation, PaymentMethod.CREDIT_CARD),
                List.of(ticket(1L, reservation)),
                100000,
                false,
                null
        );

        process.gatewayFailed("gateway timeout");

        assertThat(process.getStatus()).isEqualTo(PaymentRefundProcessStatus.GATEWAY_FAILED);
        assertThat(process.getFailureReason()).isEqualTo("gateway timeout");
        assertThat(process.getRetryCount()).isEqualTo(1);
    }

    private Payment payment(Reservation reservation, PaymentMethod method) {
        return Payment.builder()
                .paymentId(1L)
                .reservation(reservation)
                .paymentNo("PAY-1")
                .method(method)
                .status(PaymentStatus.PAID)
                .amount(100000)
                .refundedAmount(0)
                .requestedAt(LocalDateTime.now())
                .build();
    }

    private Reservation reservation() {
        return Reservation.builder()
                .reservationId(1L)
                .orderId("ORDER-1")
                .userId("user01")
                .event(Event.builder().eventId(1L).build())
                .status(ReservationStatus.PAID)
                .reservedAt(LocalDateTime.now())
                .build();
    }

    private Ticket ticket(Long ticketId, Reservation reservation) {
        return Ticket.builder()
                .ticketId(ticketId)
                .userId("user01")
                .reservation(reservation)
                .event(reservation.getEvent())
                .seat(Seat.builder().seatId(1L).price(100000).build())
                .status(TicketStatus.PAID)
                .build();
    }
}
