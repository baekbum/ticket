package dev.bum.ticket_service.service.event.policy;

import dev.bum.ticket_service.jpa.event.event.*;
import dev.bum.ticket_service.jpa.event.policy.*;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EventPolicyMaintenance {
    private final EventJpaRepository events;
    private final EventPolicyOutboxJpaRepository outbox;
    private final EventPolicyOutboxService snapshots;
    private final EntityManager entityManager;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void seedAndReconcile(Long id) {
        Event event = events.findByIdForUpdate(id).orElse(null);
        if (event == null) return;
        entityManager.refresh(event, LockModeType.PESSIMISTIC_WRITE);
        LocalDateTime now = LocalDateTime.now(clock);
        EventStatus status = event.getStatus();
        boolean changed = false;
        if (status == EventStatus.ON_SALE || status == EventStatus.SALE_ENDED || status == EventStatus.SOLD_OUT) {
            if (now.isAfter(event.getEventDateTime())) {
                changed = status != EventStatus.CLOSED;
                event.changeStatus(EventStatus.CLOSED);
            } else if (status == EventStatus.ON_SALE && event.getSaleEndAt() != null && now.isAfter(event.getSaleEndAt())) {
                changed = true;
                event.changeStatus(EventStatus.SALE_ENDED);
            }
        }
        if (changed || !outbox.existsByEventId(id)) snapshots.enqueue(event, false);
    }
}
