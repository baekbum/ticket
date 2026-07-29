package dev.bum.queue_service.service;

import dev.bum.common.service.queue.dto.QueueEnterResponse;
import dev.bum.common.service.queue.dto.QueueStatusResponse;
import dev.bum.common.service.queue.dto.QueueValidateRequest;
import dev.bum.common.service.queue.dto.QueueValidateResponse;
import dev.bum.queue_service.config.QueueProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueueService {

    private static final String STATUS_READY = "READY";
    private static final String STATUS_WAITING = "WAITING";
    private static final RedisScript<Long> ADMIT_IF_SLOT_AVAILABLE_SCRIPT = new DefaultRedisScript<>("""
            local waitingKey = KEYS[1]
            local activeKey = KEYS[2]
            local tokenKey = KEYS[3]
            local userId = ARGV[1]
            local token = ARGV[2]
            local tokenValue = ARGV[3]
            local expiresAt = tonumber(ARGV[4])
            local tokenTtlMillis = tonumber(ARGV[5])
            local admissionSize = tonumber(ARGV[6])

            local rank = redis.call('ZRANK', waitingKey, userId)
            if not rank then
                return 0
            end

            local activeCount = redis.call('ZCARD', activeKey)
            local availableSlots = admissionSize - activeCount
            if availableSlots <= 0 or rank >= availableSlots then
                return 0
            end

            redis.call('SET', tokenKey, tokenValue, 'PX', tokenTtlMillis)
            redis.call('ZADD', activeKey, expiresAt, token)
            redis.call('ZREM', waitingKey, userId)
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final QueueProperties properties;

    /**
     * 사용자가 예매 대기열에 처음 진입할 때 호출한다.
     */
    public QueueEnterResponse enter(Long eventId, String userId) {
        validateUserId(userId);
        pruneExpiredActiveTokens(eventId);

        String activeToken = findActiveToken(eventId, userId);
        if (activeToken != null) {
            return readyStatusResponse(eventId, activeToken).toEnterResponse();
        }

        redisTemplate.opsForZSet().add(waitingKey(eventId), userId, nowMillis());
        return status(eventId, userId).toEnterResponse();
    }

    /**
     * 현재 사용자의 대기열 상태를 조회한다.
     * 입장 가능한 슬롯 안에 들어오면 새 대기열 토큰을 발급한다.
     */
    public QueueStatusResponse status(Long eventId, String userId) {
        validateUserId(userId);
        pruneExpiredActiveTokens(eventId);

        String activeToken = findActiveToken(eventId, userId);
        if (activeToken != null) {
            return readyStatusResponse(eventId, activeToken);
        }

        ensureWaiting(eventId, userId);
        String token = admit(eventId, userId);
        if (token != null) {
            return readyStatusResponse(eventId, token);
        }

        activeToken = findActiveToken(eventId, userId);
        if (activeToken != null) {
            return readyStatusResponse(eventId, activeToken);
        }

        return waitingStatusResponse(eventId, userId);
    }

    public List<QueueStatusResponse> statuses(Long eventId, List<String> userIds) {
        pruneExpiredActiveTokens(eventId);
        Map<String, String> activeTokenByUserId = activeTokenByUserId(eventId);
        List<QueueStatusResponse> responses = new ArrayList<>();

        for (String userId : userIds) {
            validateUserId(userId);

            String activeToken = activeTokenByUserId.get(userId);
            if (activeToken != null) {
                responses.add(readyStatusResponse(eventId, activeToken));
                continue;
            }

            responses.add(statusWithoutPrune(eventId, userId));
        }

        return responses;
    }

    /**
     * 대기열 토큰이 요청한 이벤트와 사용자에게 발급된 값인지 검증한다.
     */
    public QueueValidateResponse validate(QueueValidateRequest request) {
        boolean valid = isTokenValid(request.eventId(), request.userId(), request.token());
        return new QueueValidateResponse(valid, valid ? "OK" : "INVALID_QUEUE_TOKEN");
    }

    public boolean complete(Long eventId, String userId, String token) {
        validateUserId(userId);
        if (!StringUtils.hasText(token) || !isTokenValid(eventId, userId, token)) {
            return false;
        }

        redisTemplate.opsForZSet().remove(activeKey(eventId), token);
        redisTemplate.delete(tokenKey(token));
        return true;
    }

    /**
     * READY 응답을 만들고 active ZSet의 score 값으로 토큰의 남은 유효 시간을 계산한다.
     */
    private QueueStatusResponse readyStatusResponse(Long eventId, String token) {
        Double score = redisTemplate.opsForZSet().score(activeKey(eventId), token);
        Long expiresAt = score == null ? null : score.longValue();
        Long expiresInSeconds = expiresAt == null ? null : Math.max(0L, (expiresAt - nowMillis()) / 1000);

        return new QueueStatusResponse(
                eventId,
                STATUS_READY,
                0L,
                redisTemplate.opsForZSet().zCard(waitingKey(eventId)),
                token,
                expiresInSeconds
        );
    }

    /**
     * 대기 중인 사용자에게 새 토큰을 발급하고 waiting ZSet에서 제거해 READY 상태로 전환한다.
     */
    private String admit(Long eventId, String userId) {
        String token = UUID.randomUUID().toString();
        long expiresAt = nowMillis() + properties.tokenTtl().toMillis();
        String tokenValue = eventId + ":" + userId;

        Long admitted = redisTemplate.execute(
                ADMIT_IF_SLOT_AVAILABLE_SCRIPT,
                List.of(waitingKey(eventId), activeKey(eventId), tokenKey(token)),
                userId,
                token,
                tokenValue,
                String.valueOf(expiresAt),
                String.valueOf(properties.tokenTtl().toMillis()),
                String.valueOf(properties.admissionSize())
        );

        return Long.valueOf(1L).equals(admitted) ? token : null;
    }

    /**
     * 토큰이 요청한 이벤트와 사용자에게 속한 값인지 확인한다.
     */
    private boolean isTokenValid(Long eventId, String userId, String token) {
        String tokenValue = redisTemplate.opsForValue().get(tokenKey(token));
        return (eventId + ":" + userId).equals(tokenValue);
    }

    private String findActiveToken(Long eventId, String userId) {
        Set<String> activeTokens = redisTemplate.opsForZSet().range(activeKey(eventId), 0, -1);
        if (activeTokens == null || activeTokens.isEmpty()) {
            return null;
        }

        String expectedValue = eventId + ":" + userId;
        for (String token : activeTokens) {
            if (expectedValue.equals(redisTemplate.opsForValue().get(tokenKey(token)))) {
                return token;
            }
        }
        return null;
    }

    private Map<String, String> activeTokenByUserId(Long eventId) {
        Set<String> activeTokens = redisTemplate.opsForZSet().range(activeKey(eventId), 0, -1);
        Map<String, String> tokenByUserId = new HashMap<>();
        if (activeTokens == null || activeTokens.isEmpty()) {
            return tokenByUserId;
        }

        String prefix = eventId + ":";
        for (String token : activeTokens) {
            String value = redisTemplate.opsForValue().get(tokenKey(token));
            if (value != null && value.startsWith(prefix)) {
                tokenByUserId.put(value.substring(prefix.length()), token);
            }
        }
        return tokenByUserId;
    }

    private QueueStatusResponse statusWithoutPrune(Long eventId, String userId) {
        ensureWaiting(eventId, userId);
        String token = admit(eventId, userId);
        if (token != null) {
            return readyStatusResponse(eventId, token);
        }

        String activeToken = findActiveToken(eventId, userId);
        if (activeToken != null) {
            return readyStatusResponse(eventId, activeToken);
        }

        return waitingStatusResponse(eventId, userId);
    }

    private void ensureWaiting(Long eventId, String userId) {
        Long rank = redisTemplate.opsForZSet().rank(waitingKey(eventId), userId);
        if (rank == null) {
            redisTemplate.opsForZSet().add(waitingKey(eventId), userId, nowMillis());
        }
    }

    private QueueStatusResponse waitingStatusResponse(Long eventId, String userId) {
        Long rank = redisTemplate.opsForZSet().rank(waitingKey(eventId), userId);
        return new QueueStatusResponse(
                eventId,
                STATUS_WAITING,
                rank == null ? null : rank + 1,
                redisTemplate.opsForZSet().zCard(waitingKey(eventId)),
                null,
                null
        );
    }

    /**
     * active ZSet에 남아 있는 만료 토큰을 제거한다.
     * 토큰 key 자체는 TTL로 사라지지만, ZSet 멤버는 별도로 제거해야 한다.
     */
    private void pruneExpiredActiveTokens(Long eventId) {
        String key = activeKey(eventId);
        Set<String> expiredTokens = redisTemplate.opsForZSet().rangeByScore(key, 0, nowMillis());
        if (expiredTokens == null || expiredTokens.isEmpty()) {
            return;
        }

        redisTemplate.opsForZSet().removeRangeByScore(key, 0, nowMillis());
        for (String token : expiredTokens) {
            redisTemplate.delete(tokenKey(token));
        }
    }

    private void validateUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("인증 정보가 올바르지 않습니다.");
        }
    }

    /**
     * Redis sorted set score에 사용할 현재 시각을 millisecond로 반환한다.
     */
    private long nowMillis() {
        return Instant.now().toEpochMilli();
    }

    /**
     * 공연별 대기 사용자 sorted set key. score는 대기열 진입 시각이다.
     */
    private String waitingKey(Long eventId) {
        return "queue:event:" + eventId + ":waiting";
    }

    /**
     * 공연별 입장 토큰 sorted set key. score는 토큰 만료 시각이다.
     */
    private String activeKey(Long eventId) {
        return "queue:event:" + eventId + ":active";
    }

    /**
     * 토큰 문자열로 eventId:userId 값을 찾는 key.
     */
    private String tokenKey(String token) {
        return "queue:token:" + token;
    }
}
