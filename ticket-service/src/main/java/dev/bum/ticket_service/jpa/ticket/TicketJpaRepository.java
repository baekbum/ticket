package dev.bum.ticket_service.jpa.ticket;

import dev.bum.ticket_service.enums.TicketStatus;
import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.jpa.reservation.Reservation;
import dev.bum.ticket_service.jpa.seat.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketJpaRepository extends JpaRepository<Ticket, Long> {
    Optional<Ticket> findByEventAndSeatAndStatus(Event event, Seat seat, TicketStatus status);
    List<Ticket> findByReservation(Reservation reservation);
    long countByUserIdAndEventAndStatus(String userId, Event event, TicketStatus status);
}
