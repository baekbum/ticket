package dev.bum.ticket_service.service.seat;

import dev.bum.ticket_service.exception.queue.QueueAccessDeniedException;
import dev.bum.ticket_service.exception.queue.QueueUnavailableException;
import dev.bum.ticket_service.service.queue.BookingSessionKey;
import dev.bum.ticket_service.service.queue.QueueAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatActivityTracker {

    private static final Duration QUERY_WINDOW = Duration.ofMinutes(1);
    private static final DefaultRedisScript<Long> RECORD_ACTIVITY = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local operation = ARGV[1]
            local observedAt = tonumber(ARGV[2])
            local expiresAt = tonumber(ARGV[3])
            local time = redis.call('TIME')
            local now = time[1] * 1000 + math.floor(time[2] / 1000)
            if (expiresAt == 0 and redis.call('EXISTS', key) == 0) or (expiresAt > 0 and expiresAt <= now) then
                return 0
            end

            if operation == 'QUERY' then
                local window = tostring(math.floor(now / tonumber(ARGV[4])))
                if redis.call('HGET', key, 'seatQueryWindow') ~= window then
                    redis.call('HSET', key, 'seatQueryWindow', window, 'seatQueryCount', 0)
                end
                redis.call('HINCRBY', key, 'seatQueryCount', 1)
                redis.call('HSETNX', key, 'firstSeatQueryAt', observedAt)
            elseif operation == 'OCCUPY' then
                redis.call('HINCRBY', key, 'seatOccupyAttempts', 1)
            elseif operation == 'PREPARE' then
                local firstQuery = tonumber(redis.call('HGET', key, 'firstSeatQueryAt'))
                if firstQuery and observedAt >= firstQuery then
                    redis.call('HSETNX', key, 'firstPrepareElapsedMs', observedAt - firstQuery)
                end
            else
                return redis.error_reply('Unsupported seat activity operation')
            end

            if expiresAt > 0 then redis.call('PEXPIREAT', key, expiresAt) end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final QueueAccessService queueAccessService;

    public void recordSeatQuery(Long eventId, String userId, String activeToken) {
        if (eventId == null || !StringUtils.hasText(userId) || !StringUtils.hasText(activeToken)) return;

        String key = activityKey(eventId, userId, activeToken);
        Instant observedAt = Instant.now();
        try {
            if (Long.valueOf(0L).equals(record(key, Operation.QUERY, observedAt, null))) {
                // 최초 집계에 필요한 만료 시각만 대기열에서 확인한다.
                Instant expiresAt = queueAccessService.validate(eventId, userId, activeToken);
                if (expiresAt != null) record(key, Operation.QUERY, observedAt, expiresAt);
            }
        } catch (QueueAccessDeniedException | QueueUnavailableException ignored) {
            // 유효한 예매 세션의 조회만 집계한다.
        } catch (DataAccessException e) {
            log.debug("예매 좌석 조회 집계 실패: eventId={}, errorType={}", eventId, e.getClass().getSimpleName());
        }
    }

    public void recordSeatOccupation(Long eventId, String userId, String activeToken, Instant expiresAt) {
        recordValidated(eventId, userId, activeToken, Operation.OCCUPY, Instant.now(), expiresAt);
    }

    public void recordPrepare(Long eventId, String userId, String activeToken, Instant requestedAt, Instant expiresAt) {
        recordValidated(eventId, userId, activeToken, Operation.PREPARE, requestedAt, expiresAt);
    }

    private void recordValidated(
            Long eventId, String userId, String activeToken, Operation operation, Instant observedAt, Instant expiresAt
    ) {
        if (expiresAt == null) return;

        try {
            record(activityKey(eventId, userId, activeToken), operation, observedAt, expiresAt);
        } catch (DataAccessException e) {
            log.debug("예매 좌석 활동 집계 실패: eventId={}, errorType={}", eventId, e.getClass().getSimpleName());
        }
    }

    private Long record(String key, Operation operation, Instant observedAt, Instant expiresAt) {
        return redis.execute(RECORD_ACTIVITY, List.of(key), operation.name(), Long.toString(observedAt.toEpochMilli()),
                expiresAt == null ? "0" : Long.toString(expiresAt.toEpochMilli()), Long.toString(QUERY_WINDOW.toMillis()));
    }

    static String activityKey(Long eventId, String userId, String activeToken) {
        return "ticket:seat-activity:v1:" + BookingSessionKey.hash(eventId, userId, activeToken);
    }

    private enum Operation {
        QUERY,
        OCCUPY,
        PREPARE
    }
}
