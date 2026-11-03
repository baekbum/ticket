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

    // 공연 데이터를 조회하고 저장하는 레포지토리.
    private final EventJpaRepository events;
    // 엔티티의 영속 상태와 데이터베이스 잠금을 관리하는 도구.
    private final EntityManager entityManager;
    // 처리 시각을 제공하는 시계.
    private final Clock clock;

    /**
     * 공연이 예매 가능한 상태와 기간인지 검증한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Event requireBookable(Long eventId, boolean existingHold) {

        Event event = events.findByIdForRead(eventId)
                .orElseThrow(() -> new IllegalArgumentException("공연 정보를 찾을 수 없습니다."));
        // 같은 영속성 컨텍스트에서 앞서 읽었더라도 잠금 아래 최신 상태로 다시 확인한다.
        entityManager.refresh(event, LockModeType.PESSIMISTIC_READ);
        validate(event, existingHold);
        return event;
    }

    /**
     * 공연 예매 잠금 처리에서 요청 값과 현재 상태가 유효한지 검증한다.
     */
    public void validate(Event event, boolean existingHold) {

        EventBookingPolicy.validate(event.getStatus(), event.getSaleStartAt(), event.getSaleEndAt(),
                event.getEventDateTime(), LocalDateTime.now(clock), existingHold);
    }
}
