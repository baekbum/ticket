package dev.bum.ticket_service.jpa.ticket;

import dev.bum.ticket_service.enums.TicketStatus;
import dev.bum.ticket_service.exception.TicketNotExistException;
import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.jpa.reservation.Reservation;
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
            jpaRepository.save(ticket); // 티켓 저장
        }
    }

    @Override
    public Ticket select(long id) {
        return jpaRepository.findById(id)
                .orElseThrow(() -> new TicketNotExistException("해당 티켓 정보는 존재하지 않습니다."));
    }

    @Override
    public List<Ticket> selectByIdList(List<Long> idList) {
        List<Ticket> tickets = jpaRepository.findAllByTicketIdIn(idList);

        if (tickets.isEmpty()) throw new TicketNotExistException("해당 티켓 정보는 존재하지 않습니다.");

        return tickets;
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
    public boolean isReservable(String userId, Event event, int selectedSeatCnt) {
        // 취소(CANCELLED)된 티켓을 제외하고, 유저가 수량을 점유하고 있는 모든 티켓 상태 정의
        List<TicketStatus> activeStatuses = List.of(
                TicketStatus.READY_TO_PAY,
                TicketStatus.AWAITING_DEPOSIT,
                TicketStatus.PAYMENT_COMPLETED
        );

        // 현재 유저가 '선점 중 + 입금 대기 중 + 결제 완료한' 티켓의 총합을 구함
        long currentReservedCount = jpaRepository.countByUserIdAndEventAndStatusIn(userId, event, activeStatuses);

        // 이미 최대치로 예매가 된 상태면 false 반환
        if (event.getMaxTicketsPerPerson() == currentReservedCount) {
            return false;
        }

        // 기존 예매 수량 + 현재 예매하려는 수량이
        // 공연의 인당 최대 예매 가능 수량을 넘지 않는지 검증
        int result = (int) (event.getMaxTicketsPerPerson() - (currentReservedCount + selectedSeatCnt));
        return -1 < result;
    }
}
