package dev.bum.queue_service.service;

import dev.bum.common.service.queue.dto.QueueStatusResponse;
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
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActiveQueueService {

    public static final String STATUS_READY = "READY";

    private static final RedisScript<Long> ADMIT_IF_SLOT_AVAILABLE_SCRIPT = new DefaultRedisScript<>("""
            local waitingKey = KEYS[1]
            local activeKey = KEYS[2]
            local activeTokenKey = KEYS[3]
            local userSessionsKey = KEYS[4]
            local waitingTokenKey = KEYS[5]
            local waitingExpiryKey = KEYS[6]
            local waitingToken = ARGV[1]
            local activeToken = ARGV[2]
            local tokenValue = ARGV[3]
            local expiresAt = tonumber(ARGV[4])
            local activeTokenTtlMillis = tonumber(ARGV[5])
            local admissionSize = tonumber(ARGV[6])
            local activeSessionMember = ARGV[7]
            local waitingSessionMember = ARGV[8]

            if redis.call('GET', waitingTokenKey) ~= tokenValue then
                return 0
            end

            local rank = redis.call('ZRANK', waitingKey, waitingToken)
            if not rank then
                return 0
            end

            local activeCount = redis.call('ZCARD', activeKey)
            local availableSlots = admissionSize - activeCount
            if availableSlots <= 0 or rank >= availableSlots then
                return 0
            end

            redis.call('SET', activeTokenKey, tokenValue, 'PX', activeTokenTtlMillis)
            redis.call('ZADD', activeKey, expiresAt, activeToken)
            redis.call('ZADD', userSessionsKey, expiresAt, activeSessionMember)
            redis.call('ZREM', waitingKey, waitingToken)
            redis.call('ZREM', userSessionsKey, waitingSessionMember)
            redis.call('ZREM', waitingExpiryKey, waitingToken)
            redis.call('DEL', waitingTokenKey)
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final QueueProperties properties;
    private final QueueRedisKeys keys;
    private final UserQueueSessionService userQueueSessionService;

    public QueueStatusResponse readyStatusResponse(Long eventId, String activeToken) {
        Double score = redisTemplate.opsForZSet().score(keys.activeKey(eventId), activeToken);
        Long expiresAt = score == null ? null : score.longValue();
        Long expiresInSeconds = expiresAt == null ? null : Math.max(0L, (expiresAt - nowMillis()) / 1000);
        Instant activeTokenExpiresAt = expiresAt == null ? null : Instant.ofEpochMilli(expiresAt);

        return new QueueStatusResponse(
                eventId,
                STATUS_READY,
                0L,
                redisTemplate.opsForZSet().zCard(keys.waitingKey(eventId)),
                activeToken,
                expiresInSeconds,
                Instant.now(),
                activeTokenExpiresAt
        );
    }

    /**
     * 진입 순서가 되어 진입 가능한 상태가 되면
     * active 토큰 발급 및 유저 세션에 토큰을 추가하고
     * waiting 토큰을 관련 set에서 모두 삭제한다.
     * @param eventId
     * @param userId
     * @param waitingToken
     * @return
     */
    public String admit(Long eventId, String userId, String waitingToken) {
        String activeToken = UUID.randomUUID().toString();
        long expiresAt = nowMillis() + properties.getActiveTokenTtl().toMillis();
        String tokenValue = tokenValue(eventId, userId);

        Long admitted = redisTemplate.execute(
                ADMIT_IF_SLOT_AVAILABLE_SCRIPT,
                List.of(
                        keys.waitingKey(eventId),
                        keys.activeKey(eventId),
                        keys.activeTokenKey(activeToken),
                        keys.userSessionsKey(userId),
                        keys.waitingTokenKey(waitingToken),
                        keys.waitingExpiryKey(eventId)
                ),
                waitingToken,
                activeToken,
                tokenValue,
                String.valueOf(expiresAt),
                String.valueOf(properties.getActiveTokenTtl().toMillis()),
                String.valueOf(properties.getAdmissionSize()),
                keys.activeSessionMember(activeToken),
                keys.waitingSessionMember(waitingToken)
        );

        return Long.valueOf(1L).equals(admitted) ? activeToken : null;
    }

    public boolean isActiveTokenValid(Long eventId, String userId, String activeToken) {
        if (!StringUtils.hasText(activeToken)) {
            return false;
        }

        String tokenValue = redisTemplate.opsForValue().get(keys.activeTokenKey(activeToken));
        if (!tokenValue(eventId, userId).equals(tokenValue)) {
            return false;
        }

        return redisTemplate.opsForZSet().score(keys.activeKey(eventId), activeToken) != null;
    }

    public Map<String, String> activeTokenByUserId(Long eventId, List<String> userIds) {
        Map<String, String> activeTokenByUserId = new HashMap<>();
        for (String userId : userIds) {
            QueueUserSession session = userQueueSessionService.activeSession(userId);
            if (session == null) {
                continue;
            }

            String activeToken = session.token();
            if (isActiveTokenValid(eventId, userId, activeToken)) {
                activeTokenByUserId.put(userId, activeToken);
            }
        }
        return activeTokenByUserId;
    }

    public boolean removeActiveToken(Long eventId, String userId, String activeToken) {
        redisTemplate.opsForZSet().remove(keys.activeKey(eventId), activeToken);
        userQueueSessionService.removeActive(userId, activeToken);
        redisTemplate.delete(keys.activeTokenKey(activeToken));
        return true;
    }

    public void pruneExpiredActiveTokens(Long eventId) {
        String key = keys.activeKey(eventId);
        Set<String> expiredTokens = redisTemplate.opsForZSet().rangeByScore(key, 0, nowMillis());
        if (expiredTokens == null || expiredTokens.isEmpty()) {
            return;
        }

        redisTemplate.opsForZSet().removeRangeByScore(key, 0, nowMillis());
        for (String activeToken : expiredTokens) {
            String tokenValue = redisTemplate.opsForValue().get(keys.activeTokenKey(activeToken));
            redisTemplate.delete(keys.activeTokenKey(activeToken));
            deleteActiveUserSessionKey(tokenValue, activeToken);
        }
    }

    public PriorityQueue<Long> activeSlotAvailableTimes(Long eventId, long now) {
        PriorityQueue<Long> slotAvailableAt = new PriorityQueue<>();
        Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> activeTokens =
                redisTemplate.opsForZSet().rangeWithScores(keys.activeKey(eventId), 0, -1);

        List<Long> activeTokenExpiresAt = new ArrayList<>();
        if (activeTokens != null) {
            activeTokens.stream()
                    .map(org.springframework.data.redis.core.ZSetOperations.TypedTuple::getScore)
                    .filter(score -> score != null)
                    .map(Double::longValue)
                    .map(expiresAt -> Math.max(now, expiresAt))
                    .sorted()
                    .forEach(activeTokenExpiresAt::add);
        }

        int admissionSize = properties.getAdmissionSize();
        int overCapacity = Math.max(0, activeTokenExpiresAt.size() - admissionSize);
        activeTokenExpiresAt.stream()
                .skip(overCapacity)
                .forEach(slotAvailableAt::add);

        int availableSlots = admissionSize - activeTokenExpiresAt.size();
        for (int slot = 0; slot < availableSlots; slot++) {
            slotAvailableAt.add(now);
        }

        return slotAvailableAt;
    }

    private void deleteActiveUserSessionKey(String tokenValue, String token) {
        if (!StringUtils.hasText(tokenValue)) {
            return;
        }

        String[] parts = tokenValue.split(":", 2);
        if (parts.length != 2) {
            return;
        }

        userQueueSessionService.removeActive(parts[1], token);
    }

    private String tokenValue(Long eventId, String userId) {
        return eventId + ":" + userId;
    }

    private long nowMillis() {
        return Instant.now().toEpochMilli();
    }
}
