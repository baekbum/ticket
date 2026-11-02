package dev.bum.ticket_service.service.event.event;

import dev.bum.common.service.ticket.event.event.policy.EventBookingPolicy;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EventBookingGuard {
    private final EventJpaRepository events;
    private final EntityManager entityManager;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public Event requireBookable(Long eventId, boolean existingHold) {
        Event event = events.findByIdForRead(eventId)
                .orElseThrow(() -> new IllegalArgumentException("공연 정보를 찾을 수 없습니다."));
        // 같은 영속성 컨텍스트에서 앞서 읽었더라도 잠금 아래 최신 상태로 다시 확인한다.
        entityManager.refresh(event, LockModeType.PESSIMISTIC_READ);
        validate(event, existingHold);
        return event;
    }

    public void validate(Event event, boolean existingHold) {
        EventBookingPolicy.validate(event.getStatus(), event.getSaleStartAt(), event.getSaleEndAt(),
                event.getEventDateTime(), LocalDateTime.now(clock), existingHold);
    }
}
