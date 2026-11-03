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

    // 공연 데이터를 조회하고 저장하는 레포지토리.
    private final EventJpaRepository events;
    // 공연 정책 데이터를 조회하고 저장하는 레포지토리.
    private final EventPolicyOutboxJpaRepository outbox;
    // 공연 정책 업무 처리를 담당하는 서비스.
    private final EventPolicyOutboxService snapshots;
    // 엔티티의 영속 상태와 데이터베이스 잠금을 관리하는 도구.
    private final EntityManager entityManager;
    // 처리 시각을 제공하는 시계.
    private final Clock clock;

    /**
     * 누락된 결제 정보를 준비하고 게이트웨이 상태와 대조한다.
     */
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
