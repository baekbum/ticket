package dev.bum.ticket_service.jpa.ticket;

import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.exception.ticket.TicketNotExistException;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
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
    public boolean isWithinPurchaseLimit(String userId, Event event, int selectedSeatCnt) {
        long currentReservedCount = countActiveTicketsByEvent(userId, event);
        return isWithinLimit(event, selectedSeatCnt, currentReservedCount);
    }

    @Override
    public boolean isWithinGroupPurchaseLimit(String userId, Event event, int selectedSeatCnt) {
        long currentReservedCount = countActiveTicketsByEventGroup(userId, event.getEventGroupCode());
        return isWithinLimit(event, selectedSeatCnt, currentReservedCount);
    }

    private boolean isWithinLimit(Event event, int selectedSeatCnt, long currentReservedCount) {
        if (event.getMaxTicketsPerPerson() == currentReservedCount) {
            return false;
        }

        int result = (int) (event.getMaxTicketsPerPerson() - (currentReservedCount + selectedSeatCnt));
        return -1 < result;
    }

    private long countActiveTicketsByEvent(String userId, Event event) {
        // 취소(CANCELLED)된 티켓을 제외하고, 유저가 수량을 점유하고 있는 모든 티켓 상태 정의
        List<TicketStatus> activeStatuses = List.of(
                TicketStatus.PENDING_PAYMENT,
                TicketStatus.PAID
        );

        // 현재 유저가 '선점 중 + 입금 대기 중 + 결제 완료한' 티켓의 총합을 구함
        return jpaRepository.countByUserIdAndEventAndStatusIn(userId, event, activeStatuses);
    }

    private long countActiveTicketsByEventGroup(String userId, String eventGroupCode) {
        List<TicketStatus> activeStatuses = List.of(
                TicketStatus.PENDING_PAYMENT,
                TicketStatus.PAID
        );

        return jpaRepository.countByUserIdAndEvent_EventGroupCodeAndStatusIn(userId, eventGroupCode, activeStatuses);
    }
}
