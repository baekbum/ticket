package dev.bum.ticket_service.service.seat;

import dev.bum.ticket_service.exception.queue.QueueAccessDeniedException;
import dev.bum.ticket_service.exception.queue.QueueUnavailableException;
import dev.bum.ticket_service.service.queue.BookingSessionKey;
import dev.bum.ticket_service.service.queue.QueueAccessService;
import dev.bum.ticket_service.service.queue.QueueAccessService.ActiveSession;
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
            local issuedAt = tonumber(ARGV[5])
            local time = redis.call('TIME')
            local now = time[1] * 1000 + math.floor(time[2] / 1000)
            if (expiresAt == 0 and redis.call('EXISTS', key) == 0) or (expiresAt > 0 and expiresAt <= now) then
                return 0
            end

            if issuedAt > 0 then redis.call('HSETNX', key, 'activeTokenIssuedAt', issuedAt) end
            if operation == 'QUERY' then
                local window = tostring(math.floor(now / tonumber(ARGV[4])))
                if redis.call('HGET', key, 'seatQueryWindow') ~= window then
                    redis.call('HSET', key, 'seatQueryWindow', window, 'seatQueryCount', 0)
                end
                redis.call('HINCRBY', key, 'seatQueryCount', 1)
                redis.call('HSETNX', key, 'firstSeatQueryAt', observedAt)
            elseif operation == 'OCCUPY' then
                redis.call('HINCRBY', key, 'seatOccupyAttempts', 1)
            elseif operation == 'CAPTCHA_PASSED' then
                redis.call('HSETNX', key, 'captchaPassedAt', observedAt)
            elseif operation == 'OCCUPIED' then
                redis.call('HSETNX', key, 'firstSeatOccupiedAt', observedAt)
            elseif operation == 'PREPARE' then
                if redis.call('HSETNX', key, 'firstPrepareRequestedAt', observedAt) == 1 then
                    local startedAt = tonumber(redis.call('HGET', key, 'activeTokenIssuedAt'))
                    if startedAt and observedAt >= startedAt then
                        redis.call('HSET', key, 'firstPrepareElapsedMs', observedAt - startedAt)
                    else
                        redis.call('HDEL', key, 'firstPrepareElapsedMs')
                    end
                end
            else
                return redis.error_reply('Unsupported seat activity operation')
            end

            if expiresAt > 0 then redis.call('PEXPIREAT', key, expiresAt) end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final QueueAccessService queueAccessService;

    /**
     * 현재 1분 구간의 좌석 조회 횟수를 늘리고 세션의 첫 조회 시각을 기록한다.
     * 활동 키가 없으면 대기열에서 Active Token을 검증한 뒤 집계를 시작한다.
     */
    public void recordSeatQuery(Long eventId, String userId, String activeToken) {
        if (eventId == null || !StringUtils.hasText(userId) || !StringUtils.hasText(activeToken)) return;

        String key = activityKey(eventId, userId, activeToken);
        Instant observedAt = Instant.now();
        try {
            if (Long.valueOf(0L).equals(record(key, Operation.QUERY, observedAt, null))) {
                // 최초 집계에 필요한 토큰 발급·만료 시각을 대기열에서 확인한다.
                ActiveSession session = queueAccessService.validate(eventId, userId, activeToken);
                if (session != null && session.expiresAt() != null) record(key, Operation.QUERY, observedAt, session);
            }
        } catch (QueueAccessDeniedException | QueueUnavailableException ignored) {
            // 유효한 예매 세션의 조회만 집계한다.
        } catch (DataAccessException e) {
            log.debug("예매 좌석 조회 집계 실패: eventId={}, errorType={}", eventId, e.getClass().getSimpleName());
        }
    }

    /**
     * 접근 검증을 통과한 뒤 실제 좌석 선점 전에 {@code seatOccupyAttempts}를 1 증가시킨다.
     * 이후 선점에 실패해도 시도 횟수에 포함된다.
     */
    public void recordSeatOccupation(Long eventId, String userId, String activeToken, ActiveSession session) {
        recordValidated(eventId, userId, activeToken, Operation.OCCUPY, Instant.now(), session);
    }

    /**
     * 캡차 정답 검증이 성공하면 세션의 첫 통과 시각을 {@code captchaPassedAt}에 저장한다.
     */
    public void recordCaptchaPassed(Long eventId, String userId, String activeToken, ActiveSession session) {
        recordValidated(eventId, userId, activeToken, Operation.CAPTCHA_PASSED, Instant.now(), session);
    }

    /**
     * 실제 좌석 선점이 성공하면 {@code firstSeatOccupiedAt}에 첫 성공 시각을 저장한다.
     * 이후 선점에 성공해도 첫 기록을 유지한다.
     */
    public void recordSeatOccupied(Long eventId, String userId, String activeToken, ActiveSession session) {
        recordValidated(eventId, userId, activeToken, Operation.OCCUPIED, Instant.now(), session);
    }

    /**
     * 검증을 통과한 첫 {@code prepare} 요청의 접수 시각을 기록한다.
     * 토큰 발급 시각이 있으면 발급부터 요청 접수까지의 시간을 밀리초로 {@code firstPrepareElapsedMs}에 저장한다.
     */
    public void recordPrepare(Long eventId, String userId, String activeToken, Instant requestedAt, ActiveSession session) {
        recordValidated(eventId, userId, activeToken, Operation.PREPARE, requestedAt, session);
    }

    /**
     * 검증된 세션의 활동을 기록한다. 세션 또는 토큰 만료 시각이 없으면 집계를 건너뛴다.
     * Redis 접근 실패는 디버그 로그에 남겨 예매 흐름을 계속 진행한다.
     */
    private void recordValidated(
            Long eventId, String userId, String activeToken, Operation operation, Instant observedAt, ActiveSession session
    ) {
        if (session == null || session.expiresAt() == null) return;

        try {
            record(activityKey(eventId, userId, activeToken), operation, observedAt, session);
        } catch (DataAccessException e) {
            log.debug("예매 좌석 활동 집계 실패: eventId={}, errorType={}", eventId, e.getClass().getSimpleName());
        }
    }

    /**
     * Redis Lua 스크립트로 활동을 기록한다. 세션 정보가 있으면 키 만료 시각을 Active Token에 맞춘다.
     *
     * @return 기록에 성공하면 1, 세션 정보와 활동 키가 모두 없거나 토큰이 만료됐으면 0
     */
    private Long record(String key, Operation operation, Instant observedAt, ActiveSession session) {
        Instant issuedAt = session == null ? null : session.issuedAt();

        return redis.execute(RECORD_ACTIVITY, List.of(key), operation.name(), Long.toString(observedAt.toEpochMilli()),
                session == null ? "0" : Long.toString(session.expiresAt().toEpochMilli()),
                Long.toString(QUERY_WINDOW.toMillis()), issuedAt == null ? "0" : Long.toString(issuedAt.toEpochMilli()));
    }

    /**
     * 공연 ID, 사용자 ID, Active Token을 해시해 세션별 Redis 활동 키를 만든다.
     */
    static String activityKey(Long eventId, String userId, String activeToken) {
        return "ticket:seat-activity:v1:" + BookingSessionKey.hash(eventId, userId, activeToken);
    }

    private enum Operation {
        QUERY,
        OCCUPY,
        CAPTCHA_PASSED,
        OCCUPIED,
        PREPARE
    }
}
