package dev.bum.ticket_service.service.reservation.reservation;

import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.ticket.Ticket;

import java.util.List;
import java.util.Objects;

public final class CancellationTicketValidator {

    private CancellationTicketValidator() {
    }

    public static void validate(Reservation reservation, List<Ticket> tickets) {
        for (Ticket ticket : tickets) {
            if (ticket.getReservation() == null
                    || !Objects.equals(ticket.getReservation().getReservationId(), reservation.getReservationId())
                    || !Objects.equals(ticket.getUserId(), reservation.getUserId())) {
                throw new IllegalArgumentException("해당 예매 소유자의 티켓만 취소할 수 있습니다.");
            }
            if (ticket.getStatus() != TicketStatus.PENDING_PAYMENT && ticket.getStatus() != TicketStatus.PAID) {
                throw new IllegalArgumentException("이미 취소되었거나 만료된 티켓은 취소할 수 없습니다.");
            }
            if (ticket.getSeat() == null
                    || (ticket.getSeat().getStatus() != SeatStatus.LOCKED
                        && ticket.getSeat().getStatus() != SeatStatus.RESERVED)) {
                throw new IllegalArgumentException("선점 또는 예매 상태인 좌석만 취소할 수 있습니다.");
            }
        }
    }
}
