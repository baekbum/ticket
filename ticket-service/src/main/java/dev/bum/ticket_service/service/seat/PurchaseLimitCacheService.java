package dev.bum.ticket_service.service.seat;

import dev.bum.common.service.ticket.ticket.enums.TicketStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.ticket.TicketJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

import static dev.bum.common.service.ticket.event.event.enums.TicketLimitScope.PER_GROUP;

@Service
@RequiredArgsConstructor
public class PurchaseLimitCacheService {

    private static final Duration CACHE_TTL = Duration.ofDays(30);
    private static final List<TicketStatus> ACTIVE_STATUSES = List.of(TicketStatus.PENDING_PAYMENT, TicketStatus.PAID);

    private final TicketJpaRepository ticketRepository;
    private final StringRedisTemplate seatRedisTemplate;

    /** 커밋이 끝난 트랜잭션을 재사용하지 않고 현재 DB 매수로 캐시를 재구성한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void refresh(Event event, String userId) {
        long count = event.getTicketLimitScope() == PER_GROUP
                ? ticketRepository.countByUserIdAndEvent_EventGroupCodeAndStatusIn(userId, event.getEventGroupCode(), ACTIVE_STATUSES)
                : ticketRepository.countByUserIdAndEventAndStatusIn(userId, event, ACTIVE_STATUSES);

        seatRedisTemplate.opsForValue().set(cacheKey(event, userId), String.valueOf(count), CACHE_TTL);
    }

    public static String cacheKey(Event event, String userId) {
        return event.getTicketLimitScope() == PER_GROUP
                ? "user:purchase:limit:group:" + event.getEventGroupCode() + ":" + userId
                : "user:purchase:limit:event:" + event.getEventId() + ":" + userId;
    }
}
