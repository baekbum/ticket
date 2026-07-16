package dev.bum.ticket_service.service.ticket;

import dev.bum.common.service.ticket.ticket.dto.TicketResponse;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final ReservationRepository reservationRepository;

    public List<TicketResponse> selectByReservationId(long reservationId) {
        Reservation reservation = reservationRepository.selectById(reservationId);

        return selectTicketsByReservation(reservation);
    }

    public List<TicketResponse> selectMyTicketsByReservationId(String currentUserId, long reservationId) {
        Reservation reservation = reservationRepository.selectById(reservationId);
        validateOwner(currentUserId, reservation);

        return selectTicketsByReservation(reservation);
    }

    private List<TicketResponse> selectTicketsByReservation(Reservation reservation) {
        return ticketRepository.selectByReservation(reservation).stream()
                .map(Ticket::toResponse)
                .toList();
    }

    private void validateOwner(String currentUserId, Reservation reservation) {
        if (!StringUtils.hasText(currentUserId) || !currentUserId.equals(reservation.getUserId())) {
            throw new AccessDeniedException("본인 예약의 티켓만 조회할 수 있습니다.");
        }
    }
}
