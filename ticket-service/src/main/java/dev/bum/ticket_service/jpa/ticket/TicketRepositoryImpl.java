package dev.bum.ticket_service.jpa.ticket;

import dev.bum.ticket_service.enums.TicketStatus;
import dev.bum.ticket_service.exception.TicketDuplicateException;
import dev.bum.ticket_service.exception.TicketNotExistException;
import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.jpa.reservation.Reservation;
import dev.bum.ticket_service.jpa.seat.Seat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class TicketRepositoryImpl implements TicketRepository {

    private final TicketJpaRepository jpaRepository;

    @Override
    public void insert(List<Ticket> tickets) {
        for (Ticket ticket : tickets) {
            isExist(ticket.getEvent(), ticket.getSeat()); // 동일한 공연의 좌석이 이미 예매 중인지 확인.

            jpaRepository.save(ticket); // 티켓 저장
        }
    }

    @Override
    public void isExist(Event event, Seat seat) {
        if (jpaRepository.findByEventAndSeatAndStatus(event, seat, TicketStatus.CONFIRMED).isPresent()) {
            throw new TicketDuplicateException("이미 동일한 티켓 정보가 존재합니다.");
        }
    }

    @Override
    public Ticket select(long id) {
        return jpaRepository.findById(id)
                .orElseThrow(() -> new TicketNotExistException("해당 티켓 정보는 존재하지 않습니다."));
    }

    @Override
    public List<Ticket> selectByReservation(Reservation reservation) {
        List<Ticket> tickets = jpaRepository.findByReservation(reservation);

        if (tickets.isEmpty()) throw new TicketNotExistException("해당 티켓 정보는 존재하지 않습니다.");

        return tickets;
    }

    @Override
    public void cancel(long id) {
        Ticket ticket = select(id);
        ticket.cancel();
    }

    @Override
    public void cancelByReservation(Reservation reservation) {
        List<Ticket> tickets = selectByReservation(reservation);

        for (Ticket ticket : tickets) {
            ticket.cancel();
        }
    }

    @Override
    public boolean isReservable(String userId, Event event) {
        long cnt = jpaRepository.countByUserIdAndEventAndStatus(userId, event, TicketStatus.CONFIRMED);

        return cnt < event.getMaxTicketsPerPerson();
    }
}
