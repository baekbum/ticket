package dev.bum.ticket_service.jpa.payment;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.payment.enums.CardCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
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

class PaymentRefundHistoryTest {

    @Test
    @DisplayName("환불 후 결제 상태와 선택 티켓 스냅샷으로 환불 이력을 생성한다")
    void create_payment_refund_history() {
        Reservation reservation = reservation();
        Payment payment = cardPayment(reservation);
        Ticket firstTicket = ticket(1L, reservation);
        Ticket secondTicket = ticket(2L, reservation);

        payment.partialRefund(125000);

        PaymentRefundHistory history = PaymentRefundHistory.create(
                payment,
                List.of(firstTicket, secondTicket),
                125000,
                false
        );

        assertThat(history.getPayment()).isEqualTo(payment);
        assertThat(history.getReservation()).isEqualTo(reservation);
        assertThat(history.getPaymentNo()).isEqualTo("PAY-1");
        assertThat(history.getMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(history.getRefundAmount()).isEqualTo(125000);
        assertThat(history.getRefundedAmountAfter()).isEqualTo(125000);
        assertThat(history.getRefundableAmountAfter()).isEqualTo(125000);
        assertThat(history.getPaymentStatusAfter()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(history.isFullCancellation()).isFalse();
        assertThat(history.getTickets()).hasSize(2);
        assertThat(history.getTickets().get(0).getTicket()).isEqualTo(firstTicket);
        assertThat(history.getTickets().get(0).getTicketPrice()).isEqualTo(150000);
    }

    private Payment cardPayment(Reservation reservation) {
        return Payment.builder()
                .reservation(reservation)
                .paymentNo("PAY-1")
                .method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.PAID)
                .amount(250000)
                .cardInfo(CardPaymentInfo.builder()
                        .transactionId("CARD-1")
                        .cardCompany(CardCompany.SHINHAN)
                        .maskedCardNumber("4111-****-****-1111")
                        .build())
                .requestedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();
    }

    private Reservation reservation() {
        return Reservation.builder()
                .reservationId(1L)
                .orderId("order-1")
                .userId("user01")
                .event(event())
                .status(ReservationStatus.PAID)
                .reservedAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build();
    }

    private Ticket ticket(Long ticketId, Reservation reservation) {
        return Ticket.builder()
                .ticketId(ticketId)
                .userId(reservation.getUserId())
                .reservation(reservation)
                .event(reservation.getEvent())
                .seat(seat(ticketId, reservation.getEvent()))
                .status(TicketStatus.PAID)
                .build();
    }

    private Event event() {
        return Event.builder()
                .eventId(1L)
                .artistName("IU")
                .title("IU Concert")
                .venue("KSPO Dome")
                .eventDateTime(LocalDateTime.of(2026, 9, 18, 18, 0))
                .status(EventStatus.ON_SALE)
                .maxTicketsPerPerson(4)
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
                .price(150000)
                .status(SeatStatus.AVAILABLE)
                .build();
    }
}
