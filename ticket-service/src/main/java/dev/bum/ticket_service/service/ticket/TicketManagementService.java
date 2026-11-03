package dev.bum.ticket_service.service.ticket;

import dev.bum.common.service.ticket.ticket.dto.TicketResponse;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.reservation.reservation.ReservationRepository;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TicketManagementService {

    // 티켓 데이터를 조회하고 저장하는 레포지토리.
    private final TicketRepository ticketRepository;
    // 예매 데이터를 조회하고 저장하는 레포지토리.
    private final ReservationRepository reservationRepository;

    /**
     * 관리자 기준으로 예매 ID에 연결된 모든 티켓 목록을 조회한다.
     */
    public List<TicketResponse> selectByReservationId(long reservationId) {

        Reservation reservation = reservationRepository.selectById(reservationId);

        return ticketRepository.selectByReservation(reservation).stream()
                .map(Ticket::toResponse)
                .toList();
    }
}
