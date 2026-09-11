package dev.bum.queue_service.service;

import dev.bum.common.service.queue.dto.QueueStatusResponse;
import dev.bum.queue_service.config.QueueProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WaitingQueueService {

    public static final String STATUS_WAITING = "WAITING";

    private final StringRedisTemplate redisTemplate;
    private final QueueProperties properties;
    private final QueueRedisKeys keys;
    private final ActiveQueueService activeQueueService;
    private final UserQueueSessionService userQueueSessionService;

    public String createWaiting(Long eventId, String userId) {
        String waitingToken = UUID.randomUUID().toString();
        redisTemplate.opsForZSet().add(keys.waitingKey(eventId), waitingToken, nowMillis());
        refreshWaitingToken(eventId, userId, waitingToken);
        return waitingToken;
    }

    public QueueStatusResponse waitingStatusResponse(Long eventId, String userId, String waitingToken) {
        Long rank = redisTemplate.opsForZSet().rank(keys.waitingKey(eventId), waitingToken);
        Long currentRank = rank == null ? null : rank + 1;
        Instant estimatedEntryAt = estimatedEntryAt(eventId, currentRank);
        Instant activeTokenExpiresAt = estimatedEntryAt == null
                ? null
                : estimatedEntryAt.plus(properties.getActiveTokenTtl());
        return new QueueStatusResponse(
                eventId,
                STATUS_WAITING,
                currentRank,
                redisTemplate.opsForZSet().zCard(keys.waitingKey(eventId)),
                waitingToken,
                properties.getWaitingTokenTtl().toSeconds(),
                estimatedEntryAt,
                activeTokenExpiresAt
        );
    }

    /**
     * 토큰 값이 유효한지 확인하는 메서드
     * @param eventId
     * @param userId
     * @param waitingToken
     * @return
     */
    public boolean isWaitingTokenValid(Long eventId, String userId, String waitingToken) {
        if (!StringUtils.hasText(waitingToken)) {
            return false;
        }

        String tokenValue = redisTemplate.opsForValue().get(keys.waitingTokenKey(waitingToken));
        if (!tokenValue(eventId, userId).equals(tokenValue)) {
            return false;
        }

        return redisTemplate.opsForZSet().score(keys.waitingKey(eventId), waitingToken) != null;
    }

    public void removeWaitingToken(Long eventId, String userId, String waitingToken) {
        redisTemplate.opsForZSet().remove(keys.waitingKey(eventId), waitingToken);
        redisTemplate.opsForZSet().remove(keys.waitingExpiryKey(eventId), waitingToken);
        userQueueSessionService.removeWaiting(userId, waitingToken);
        redisTemplate.delete(keys.waitingTokenKey(waitingToken));
    }

    public Long waitingCount(Long eventId) {
        return redisTemplate.opsForZSet().zCard(keys.waitingKey(eventId));
    }

    public void pruneExpiredWaitingTokens(Long eventId) {
        pruneExpiredWaitingTokens(eventId, null);
    }

    public void pruneExpiredWaitingTokens(Long eventId, String userId) {
        // 대기열에 waiting 토큰을 발급 받았지만, 추가적인 요청이 없어서 토큰이 갱신되지 않아 만료된 토큰을 찾는 작업
        // 해당 토큰이 만료되는 시간을 score로 기록해놨음
        Set<String> waitingTokens = redisTemplate.opsForZSet().rangeByScore(keys.waitingExpiryKey(eventId), 0, nowMillis());
        if (waitingTokens == null || waitingTokens.isEmpty()) {
            return;
        }

        for (String waitingToken : waitingTokens) {
            // 만료된 웨이팅 토큰을 waiting zSet에서 삭제함
            redisTemplate.opsForZSet().remove(keys.waitingKey(eventId), waitingToken);

            // 만료된 웨이팅 토큰을 찾기 위한 역방향 키를 waitingExpiry zSet에서 삭제함
            redisTemplate.opsForZSet().remove(keys.waitingExpiryKey(eventId), waitingToken);

            // TTL 시간이 지나면 자동적으로 사라지지만 명시적으로 발급 받은 waiting 토큰을 Set에서 삭제함
            redisTemplate.delete(keys.waitingTokenKey(waitingToken));

            userQueueSessionService.removeWaiting(userId, waitingToken);
        }
    }

    /**
     * waiting 토큰을 갱신하고,
     * 만료된 토큰을 waiting zSet에서 삭제하기위해 waitingExpiry zSet에 토큰 추가
     * 유저 세션 zSet에 토큰 추가.
     * @param eventId
     * @param userId
     * @param waitingToken
     */
    public void refreshWaitingToken(Long eventId, String userId, String waitingToken) {
        redisTemplate.opsForValue().set(
                keys.waitingTokenKey(waitingToken),
                tokenValue(eventId, userId),
                properties.getWaitingTokenTtl()
        );

        long expiresAt = nowMillis() + properties.getWaitingTokenTtl().toMillis();
        userQueueSessionService.addWaiting(userId, waitingToken, expiresAt);
        redisTemplate.opsForZSet().add(
                keys.waitingExpiryKey(eventId),
                waitingToken,
                expiresAt
        );
    }

    private Instant estimatedEntryAt(Long eventId, Long currentRank) {
        if (currentRank == null || currentRank <= 0) {
            return null;
        }

        long now = nowMillis();
        PriorityQueue<Long> slotAvailableAt = activeQueueService.activeSlotAvailableTimes(eventId, now);
        if (slotAvailableAt.isEmpty()) {
            return Instant.ofEpochMilli(now);
        }

        long estimatedEntryAt = now;
        for (long position = 0; position < currentRank; position++) {
            estimatedEntryAt = Math.max(now, slotAvailableAt.poll());
            slotAvailableAt.add(estimatedEntryAt + properties.getActiveTokenTtl().toMillis());
        }

        return Instant.ofEpochMilli(estimatedEntryAt);
    }

    private String tokenValue(Long eventId, String userId) {
        return eventId + ":" + userId;
    }

    private long nowMillis() {
        return Instant.now().toEpochMilli();
    }
}
