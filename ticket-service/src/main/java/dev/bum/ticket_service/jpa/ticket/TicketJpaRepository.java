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
    List<Ticket> findAllByTicketIdIn(List<Long> ticketIdList);
    List<Ticket> findByReservation(Reservation reservation);

    // 특정 유저가 특정 공연에 대해 '지정한 상태들'로 가지고 있는 티켓의 총 개수를 구함
    long countByUserIdAndEventAndStatusIn(String userId, Event event, List<TicketStatus> statuses);
}
