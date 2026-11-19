package dev.bum.ticket_service.service.reservation.reservation;

import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.enums.ReservationStatus;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.jpa.payment.payment.*;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.*;
import dev.bum.ticket_service.service.payment.refund.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class RetiredPaidCancellationTest {

    @Mock TicketJpaRepository tickets;
    @Mock PaymentJpaRepository payments;
    @Mock PaymentRefundProcessService processes;
    @InjectMocks ReservationCancellationService cancellation;

    @ParameterizedTest
    @EnumSource(value=PaymentMethod.class,names={"CREDIT_CARD","BANK_TRANSFER"})
    void ordinaryCancelNeverExecutesTheRetiredRefundPath(PaymentMethod method) {
        Reservation reservation=Reservation.builder().reservationId(1L).userId("owner").status(ReservationStatus.PAID).build();
        Seat seat=Seat.builder().seatId(1L).status(SeatStatus.RESERVED).build();
        Ticket ticket=Ticket.builder().ticketId(1L).reservation(reservation).userId("owner").seat(seat).status(TicketStatus.PAID).build();
        Payment payment=Payment.builder().paymentNo("PAY-1").reservation(reservation).method(method).status(PaymentStatus.PAID).amount(10000).build();
        when(tickets.findByReservation(reservation)).thenReturn(List.of(ticket));
        when(payments.findByReservation(reservation)).thenReturn(Optional.of(payment));
        CancelReservationRequest request=new CancelReservationRequest();
        request.setSelectedTicketIdList(List.of(1L));

        assertThatThrownBy(()->cancellation.cancel(reservation,request)).isInstanceOf(IllegalStateException.class)
                .hasMessage("결제 완료된 예매의 환불은 환불 신청 API를 이용해주세요.");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PAID);
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.RESERVED);
    }
}
