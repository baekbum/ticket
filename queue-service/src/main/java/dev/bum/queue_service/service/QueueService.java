package dev.bum.queue_service.service;

import dev.bum.common.service.queue.dto.QueueEnterResponse;
import dev.bum.common.service.queue.dto.QueueStatusResponse;
import dev.bum.common.service.queue.dto.QueueValidateRequest;
import dev.bum.common.service.queue.dto.QueueValidateResponse;
import dev.bum.queue_service.config.QueueProperties;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
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
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private static final String STATUS_READY = "READY";
    private static final String STATUS_WAITING = "WAITING";
    private static final RedisScript<Long> ADMIT_IF_SLOT_AVAILABLE_SCRIPT = new DefaultRedisScript<>("""
            local waitingKey = KEYS[1]
            local activeKey = KEYS[2]
            local tokenKey = KEYS[3]
            local activeUserKey = KEYS[4]
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
            redis.call('SET', activeUserKey, token, 'PX', tokenTtlMillis)
            redis.call('ZADD', activeKey, expiresAt, token)
            redis.call('ZREM', waitingKey, userId)
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final QueueProperties properties;

    /**
     * 사용자의 대기열 최초 진입 요청을 처리한다.
     * 유효한 큐 토큰이 있으면 READY를 복구하고, 없으면 대기열에 등록한 뒤 입장 가능 여부를 확인한다.
     */
    @Observed(name = "queue.enter", contextualName = "queue enter")
    public QueueEnterResponse enter(Long eventId, String userId, String queueToken) {
        return executeWithRedisLogging("enter", eventId, userId, queueToken, () -> {
            validateUserId(userId);
            pruneExpiredActiveTokens(eventId);

            QueueStatusResponse readyResponse = readyStatusIfValidToken(eventId, userId, queueToken);
            if (readyResponse != null) {
                return readyResponse.toEnterResponse();
            }

            return waitOrAdmitWithoutPrune(eventId, userId).toEnterResponse();
        });
    }

    /**
     * 사용자의 현재 대기열 상태를 조회한다.
     * 유효한 큐 토큰이 있으면 READY를 반환하고, 없으면 대기열 기준으로 입장을 다시 시도한다.
     */
    @Observed(name = "queue.status", contextualName = "queue status")
    public QueueStatusResponse status(Long eventId, String userId, String queueToken) {
        return executeWithRedisLogging("status", eventId, userId, queueToken, () -> {
            validateUserId(userId);
            pruneExpiredActiveTokens(eventId);

            QueueStatusResponse readyResponse = readyStatusIfValidToken(eventId, userId, queueToken);
            if (readyResponse != null) {
                return readyResponse;
            }

            return waitOrAdmitWithoutPrune(eventId, userId);
        });
    }

    /**
     * 여러 사용자의 대기열 상태를 한 번에 조회한다.
     * active-user 역방향 키를 먼저 확인하고, READY가 아니면 사용자별로 입장을 시도한다.
     */
    @Observed(name = "queue.statuses", contextualName = "queue bulk statuses")
    public List<QueueStatusResponse> statuses(Long eventId, List<String> userIds) {
        return executeWithRedisLogging("statuses", eventId, String.join(",", userIds), null, () -> {
            pruneExpiredActiveTokens(eventId);
            Map<String, String> activeTokenByUserId = activeTokenByUserId(eventId, userIds);
            List<QueueStatusResponse> responses = new ArrayList<>();

            for (String userId : userIds) {
                validateUserId(userId);

                String activeToken = activeTokenByUserId.get(userId);
                if (activeToken != null) {
                    responses.add(readyStatusResponse(eventId, activeToken));
                    continue;
                }

                responses.add(waitOrAdmitWithoutPrune(eventId, userId));
            }

            return responses;
        });
    }

    /**
     * ticket-service가 전달한 큐 토큰이 해당 이벤트와 사용자에 대해 유효한 active token인지 검증한다.
     */
    @Observed(name = "queue.validate-token", contextualName = "queue validate token")
    public QueueValidateResponse validate(QueueValidateRequest request) {
        return executeWithRedisLogging("validate", request.eventId(), request.userId(), request.token(), () -> {
            boolean valid = isActiveTokenValid(request.eventId(), request.userId(), request.token());
            return new QueueValidateResponse(valid, valid ? "OK" : "INVALID_QUEUE_TOKEN");
        });
    }

    /**
     * 예매 또는 결제 흐름이 끝난 사용자의 active token을 회수한다.
     * active ZSet, token key, active-user 역방향 키를 함께 제거해 다음 사용자가 입장할 수 있게 한다.
     */
    @Observed(name = "queue.complete", contextualName = "queue complete")
    public boolean complete(Long eventId, String userId, String token) {
        return executeWithRedisLogging("complete", eventId, userId, token, () -> {
            validateUserId(userId);
            if (!isActiveTokenValid(eventId, userId, token)) {
                return false;
            }

            redisTemplate.opsForZSet().remove(activeKey(eventId), token);
            redisTemplate.delete(List.of(tokenKey(token), activeUserKey(eventId, userId)));
            return true;
        });
    }

    private <T> T executeWithRedisLogging(
            String operation,
            Long eventId,
            String userId,
            String token,
            Supplier<T> supplier
    ) {
        try {
            return supplier.get();
        } catch (DataAccessException e) {
            log.error("[REDIS-ERROR] Queue Redis 처리 실패. operation={}, keyPrefix=queue, eventId={}, userId={}, token={}",
                    operation, eventId, userId, token, e);
            throw e;
        }
    }

    /**
     * 클라이언트가 제시한 큐 토큰이 유효하면 READY 응답을 만든다.
     * 토큰이 없거나 만료/불일치하면 null을 반환해 일반 대기열 흐름으로 넘긴다.
     */
    private QueueStatusResponse readyStatusIfValidToken(Long eventId, String userId, String queueToken) {
        if (!isActiveTokenValid(eventId, userId, queueToken)) {
            return null;
        }

        return readyStatusResponse(eventId, queueToken);
    }

    /**
     * 만료 토큰 정리가 이미 끝난 상태에서 사용자를 대기열에 보장 등록하고 입장을 시도한다.
     * 입장 성공 시 READY, 실패 시 WAITING 응답을 반환한다.
     */
    private QueueStatusResponse waitOrAdmitWithoutPrune(Long eventId, String userId) {
        ensureWaiting(eventId, userId);
        String token = admit(eventId, userId);
        if (token != null) {
            return readyStatusResponse(eventId, token);
        }

        return waitingStatusResponse(eventId, userId);
    }

    /**
     * active token의 만료 시각을 기준으로 READY 응답 DTO를 만든다.
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
     * waiting queue에 등록된 사용자가 active slot에 들어갈 수 있는지 확인하고 가능하면 토큰을 발급한다.
     * Redis Lua script로 rank 확인, active slot 확인, token 저장, waiting 제거를 원자적으로 처리한다.
     */
    private String admit(Long eventId, String userId) {
        String token = UUID.randomUUID().toString();
        long expiresAt = nowMillis() + properties.getTokenTtl().toMillis();
        String tokenValue = eventId + ":" + userId;

        Long admitted = redisTemplate.execute(
                ADMIT_IF_SLOT_AVAILABLE_SCRIPT,
                List.of(waitingKey(eventId), activeKey(eventId), tokenKey(token), activeUserKey(eventId, userId)),
                userId,
                token,
                tokenValue,
                String.valueOf(expiresAt),
                String.valueOf(properties.getTokenTtl().toMillis()),
                String.valueOf(properties.getAdmissionSize())
        );

        return Long.valueOf(1L).equals(admitted) ? token : null;
    }

    /**
     * token key 값과 active ZSet 존재 여부를 함께 확인해 큐 토큰의 유효성을 검증한다.
     */
    private boolean isActiveTokenValid(Long eventId, String userId, String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }

        String tokenValue = redisTemplate.opsForValue().get(tokenKey(token));
        if (!(eventId + ":" + userId).equals(tokenValue)) {
            return false;
        }

        return redisTemplate.opsForZSet().score(activeKey(eventId), token) != null;
    }

    /**
     * 사용자별 active-user 역방향 키로 active token을 조회한다.
     * 조회된 토큰은 실제 active ZSet에도 남아 있는지 다시 검증한다.
     */
    private Map<String, String> activeTokenByUserId(Long eventId, List<String> userIds) {
        Map<String, String> tokenByUserId = new HashMap<>();
        for (String userId : userIds) {
            String token = redisTemplate.opsForValue().get(activeUserKey(eventId, userId));
            if (isActiveTokenValid(eventId, userId, token)) {
                tokenByUserId.put(userId, token);
            }
        }
        return tokenByUserId;
    }

    /**
     * 사용자가 waiting queue에 없으면 현재 시각 score로 등록한다.
     * 이미 대기 중이면 기존 rank를 유지하기 위해 score를 갱신하지 않는다.
     */
    private void ensureWaiting(Long eventId, String userId) {
        Long rank = redisTemplate.opsForZSet().rank(waitingKey(eventId), userId);
        if (rank == null) {
            redisTemplate.opsForZSet().add(waitingKey(eventId), userId, nowMillis());
        }
    }

    /**
     * waiting queue의 현재 rank와 전체 대기 수를 기준으로 WAITING 응답 DTO를 만든다.
     */
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
     * token key와 active-user 역방향 키도 함께 정리한다.
     */
    private void pruneExpiredActiveTokens(Long eventId) {
        String key = activeKey(eventId);
        Set<String> expiredTokens = redisTemplate.opsForZSet().rangeByScore(key, 0, nowMillis());
        if (expiredTokens == null || expiredTokens.isEmpty()) {
            return;
        }

        redisTemplate.opsForZSet().removeRangeByScore(key, 0, nowMillis());
        for (String token : expiredTokens) {
            String tokenValue = redisTemplate.opsForValue().get(tokenKey(token));
            redisTemplate.delete(tokenKey(token));
            deleteActiveUserKey(tokenValue);
        }
    }

    /**
     * token value(eventId:userId)를 파싱해 active-user 역방향 키를 제거한다.
     */
    private void deleteActiveUserKey(String tokenValue) {
        if (!StringUtils.hasText(tokenValue)) {
            return;
        }

        String[] parts = tokenValue.split(":", 2);
        if (parts.length != 2) {
            return;
        }

        redisTemplate.delete(activeUserKey(Long.valueOf(parts[0]), parts[1]));
    }

    /**
     * 인증 필터에서 전달된 사용자 ID가 비어 있지 않은지 확인한다.
     */
    private void validateUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("Invalid user id.");
        }
    }

    /**
     * Redis ZSet score와 token 만료 계산에 사용할 현재 epoch millisecond를 반환한다.
     */
    private long nowMillis() {
        return Instant.now().toEpochMilli();
    }

    /**
     * 이벤트별 waiting queue ZSet key를 만든다.
     */
    private String waitingKey(Long eventId) {
        return "queue:event:" + eventId + ":waiting";
    }

    /**
     * 이벤트별 active token ZSet key를 만든다.
     */
    private String activeKey(Long eventId) {
        return "queue:event:" + eventId + ":active";
    }

    /**
     * token 문자열로 token value(eventId:userId)를 찾는 key를 만든다.
     */
    private String tokenKey(String token) {
        return "queue:token:" + token;
    }

    /**
     * 이벤트와 사용자 기준으로 active token을 직접 조회하는 역방향 key를 만든다.
     */
    private String activeUserKey(Long eventId, String userId) {
        return "queue:active-user:" + eventId + ":" + userId;
    }
}
