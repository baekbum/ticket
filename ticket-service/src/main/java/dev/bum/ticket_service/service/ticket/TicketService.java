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

    /**
     * 관리자 기준으로 예매 ID에 연결된 모든 티켓 목록을 조회한다.
     */
    public List<TicketResponse> selectByReservationId(long reservationId) {
        Reservation reservation = reservationRepository.selectById(reservationId);

        return selectTicketsByReservation(reservation);
    }

    /**
     * 로그인 사용자가 본인 예매에 연결된 티켓 목록을 조회한다.
     */
    public List<TicketResponse> selectMyTicketsByReservationId(String currentUserId, long reservationId) {
        Reservation reservation = reservationRepository.selectById(reservationId);
        validateOwner(currentUserId, reservation);

        return selectTicketsByReservation(reservation);
    }

    /**
     * 예매 엔티티에 연결된 티켓을 응답 DTO 목록으로 변환한다.
     */
    private List<TicketResponse> selectTicketsByReservation(Reservation reservation) {
        return ticketRepository.selectByReservation(reservation).stream()
                .map(Ticket::toResponse)
                .toList();
    }

    /**
     * 요청 사용자가 예매 소유자인지 검증한다.
     */
    private void validateOwner(String currentUserId, Reservation reservation) {
        if (!StringUtils.hasText(currentUserId) || !currentUserId.equals(reservation.getUserId())) {
            throw new AccessDeniedException("본인 예약의 티켓만 조회할 수 있습니다.");
        }
    }
}
