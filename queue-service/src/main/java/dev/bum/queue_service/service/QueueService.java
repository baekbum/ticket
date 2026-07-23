package dev.bum.queue_service.service;

import dev.bum.queue_service.config.QueueProperties;
import dev.bum.queue_service.dto.QueueEnterResponse;
import dev.bum.queue_service.dto.QueueStatusResponse;
import dev.bum.queue_service.dto.QueueValidateRequest;
import dev.bum.queue_service.dto.QueueValidateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueueService {

    private static final String STATUS_READY = "READY";
    private static final String STATUS_WAITING = "WAITING";

    private final StringRedisTemplate redisTemplate;
    private final QueueProperties properties;

    /**
     * 사용자가 예매 대기열에 처음 진입할 때 호출한다.
     */
    public QueueEnterResponse enter(Long eventId, String userId) {
        validateUserId(userId);
        pruneExpiredActiveTokens(eventId);

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

        Long rank = redisTemplate.opsForZSet().rank(waitingKey(eventId), userId); // 현재 내가 몇번째 순서인지 확인.
        if (rank == null) {
            redisTemplate.opsForZSet().add(waitingKey(eventId), userId, nowMillis());
            rank = redisTemplate.opsForZSet().rank(waitingKey(eventId), userId);
        }

        long activeCount = activeCount(eventId); // 현재 토큰을 가지고 티켓팅 중인 인원 수
        long availableSlots = Math.max(0L, properties.admissionSize() - activeCount); // 추가적으로 들어갈 수 있는 인원 수

        if (rank != null && rank < availableSlots) {
            String token = admit(eventId, userId);
            return readyStatusResponse(eventId, token);
        }

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
     * 대기열 토큰이 요청한 이벤트와 사용자에게 발급된 값인지 검증한다.
     */
    public QueueValidateResponse validate(QueueValidateRequest request) {
        boolean valid = isTokenValid(request.eventId(), request.userId(), request.token());
        return new QueueValidateResponse(valid, valid ? "OK" : "INVALID_QUEUE_TOKEN");
    }

    /**
     * READY 응답을 만들고 active ZSet의 score 값으로 토큰의 남은 유효 시간을 계산한다.
     */
    private QueueStatusResponse readyStatusResponse(Long eventId, String token) {
        Double score = redisTemplate.opsForZSet().score(activeKey(eventId), token);
        Long expiresAt = score == null ? null : score.longValue();

        Long expiresInSeconds = expiresAt == null
                ? null
                : Math.max(0L, (expiresAt - nowMillis()) / 1000);

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

        redisTemplate.opsForValue().set(tokenKey(token), tokenValue, properties.tokenTtl()); // 토큰 값 저장 (만료 시간 포함)
        redisTemplate.opsForZSet().add(activeKey(eventId), token, expiresAt); // 대기열을 통과한 Zset에 해당 유저를 추가.
        redisTemplate.opsForZSet().remove(waitingKey(eventId), userId); // 대기열를 위한 key를 삭제.

        return token;
    }

    /**
     * 토큰이 요청한 이벤트와 사용자에게 속한 값인지 확인한다.
     */
    private boolean isTokenValid(Long eventId, String userId, String token) {
        String tokenValue = redisTemplate.opsForValue().get(tokenKey(token));
        return (eventId + ":" + userId).equals(tokenValue);
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

    /**
     * 현재 입장 토큰을 발급받은 사용자 수를 조회한다.
     */
    private long activeCount(Long eventId) {
        Long count = redisTemplate.opsForZSet().zCard(activeKey(eventId));
        return count == null ? 0 : count;
    }

    /**
     * 인증 필터에서 전달된 사용자 ID가 있는지 검증한다.
     */
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
