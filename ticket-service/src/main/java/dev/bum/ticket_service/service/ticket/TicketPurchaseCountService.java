package dev.bum.ticket_service.service.ticket;

import dev.bum.common.service.ticket.event.event.enums.TicketLimitScope;
import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import dev.bum.ticket_service.jpa.ticket.TicketPurchaseLock;
import dev.bum.ticket_service.jpa.ticket.TicketPurchaseLockJpaRepository;
import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class TicketPurchaseCountService {

    private static final List<TicketStatus> ACTIVE_STATUSES = List.of(TicketStatus.PENDING_PAYMENT, TicketStatus.PAID);
    private final TicketPurchaseLockJpaRepository locks;
    private final EntityManager entityManager;

    public void validate(Event event, String userId, int amount) {
        lock(event, userId).validateIncrease(amount, event.getMaxTicketsPerPerson());
    }

    public void reserve(Event event, String userId, int amount) {
        lock(event, userId).increase(amount, event.getMaxTicketsPerPerson());
    }

    /** 티켓 상태를 변경하기 전에 호출해 같은 트랜잭션에서 카운트를 차감한다. */
    public void release(Reservation reservation, List<Ticket> selectedTickets) {
        TicketPurchaseLock count = lock(reservation.getEvent(), reservation.getUserId());
        // 잠금을 기다리는 동안 다른 취소가 완료되었을 수 있으므로 최신 티켓 상태를 확인한다.
        selectedTickets.forEach(ticket -> entityManager.refresh(ticket, LockModeType.PESSIMISTIC_WRITE));
        count.decrease(activeCount(selectedTickets));
    }

    public void prepareStatusAdjustment(Reservation reservation, List<Ticket> selectedTickets) {
        lock(reservation.getEvent(), reservation.getUserId());
        selectedTickets.forEach(ticket -> entityManager.refresh(ticket, LockModeType.PESSIMISTIC_WRITE));
    }

    public void applyStatusAdjustment(Reservation reservation, long beforeCount, List<Ticket> changedTickets) {
        long delta = activeCount(changedTickets) - beforeCount;

        if (delta == 0) return;

        TicketPurchaseLock count = lock(reservation.getEvent(), reservation.getUserId());

        if (delta > 0) count.increase(delta, reservation.getEvent().getMaxTicketsPerPerson());
        else count.decrease(-delta);
    }

    public static long activeCount(List<Ticket> selectedTickets) {
        return selectedTickets.stream().filter(ticket -> ACTIVE_STATUSES.contains(ticket.getStatus())).count();
    }

    private TicketPurchaseLock lock(Event event, String userId) {
        TicketLimitScope scope = event.getTicketLimitScope();
        String key = scope == TicketLimitScope.PER_GROUP ? event.getEventGroupCode() : event.getEventId().toString();
        return locks.findForUpdate(userId, scope, key).orElseGet(() -> {
            locks.insertIfAbsent(userId, scope.name(), key, 0);

            return locks.findForUpdate(userId, scope, key)
                    .orElseThrow(() -> new IllegalStateException("구매 매수 정보를 찾을 수 없습니다."));
        });
    }
}
