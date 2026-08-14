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
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
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
            local activeTokenKey = KEYS[3]
            local activeUserKey = KEYS[4]
            local waitingTokenKey = KEYS[5]
            local waitingUserKey = KEYS[6]
            local waitingExpiryKey = KEYS[7]
            local waitingToken = ARGV[1]
            local activeToken = ARGV[2]
            local tokenValue = ARGV[3]
            local expiresAt = tonumber(ARGV[4])
            local activeTokenTtlMillis = tonumber(ARGV[5])
            local admissionSize = tonumber(ARGV[6])

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
            redis.call('SET', activeUserKey, activeToken, 'PX', activeTokenTtlMillis)
            redis.call('ZADD', activeKey, expiresAt, activeToken)
            redis.call('ZREM', waitingKey, waitingToken)
            redis.call('ZREM', waitingExpiryKey, waitingToken)
            redis.call('DEL', waitingTokenKey)
            redis.call('DEL', waitingUserKey)
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final QueueProperties properties;

    /**
     * 사용자의 대기열 최초 진입 요청을 처리한다.
     * 유효한 active token이 있으면 READY를 복구하고, 없으면 대기열에 등록한 뒤 입장 가능 여부를 확인한다.
     */
    @Observed(name = "queue.enter", contextualName = "queue enter")
    public QueueEnterResponse enter(Long eventId, String userId, String clientToken) {
        return executeWithRedisLogging("enter", eventId, userId, clientToken, () -> {
            validateUserId(userId);

            QueueStatusResponse readyResponse = readyStatusIfValidActiveToken(eventId, userId, clientToken);
            if (readyResponse != null) {
                return readyResponse.toEnterResponse();
            }

            return waitOrAdmit(eventId, userId, clientToken).toEnterResponse();
        });
    }

    /**
     * 사용자의 현재 대기열 상태를 조회한다.
     * 유효한 active token이 있으면 READY를 반환하고, 없으면 대기열 기준으로 입장을 다시 시도한다.
     */
    @Observed(name = "queue.status", contextualName = "queue status")
    public QueueStatusResponse status(Long eventId, String userId, String clientToken) {
        return executeWithRedisLogging("status", eventId, userId, clientToken, () -> {
            validateUserId(userId);

            QueueStatusResponse readyResponse = readyStatusIfValidActiveToken(eventId, userId, clientToken);
            if (readyResponse != null) {
                return readyResponse;
            }

            return waitOrAdmit(eventId, userId, clientToken);
        });
    }

    /**
     * 여러 사용자의 대기열 상태를 한 번에 조회한다.
     * active-user 역방향 키를 먼저 확인하고, READY가 아니면 사용자별로 입장을 시도한다.
     */
    @Observed(name = "queue.statuses", contextualName = "queue bulk statuses")
    public List<QueueStatusResponse> statuses(Long eventId, List<String> userIds) {
        return executeWithRedisLogging("statuses", eventId, String.join(",", userIds), null, () -> {
            Map<String, String> activeTokenByUserId = activeTokenByUserId(eventId, userIds);
            List<QueueStatusResponse> responses = new ArrayList<>();

            for (String userId : userIds) {
                validateUserId(userId);

                String activeToken = activeTokenByUserId.get(userId);
                if (activeToken != null) {
                    responses.add(readyStatusResponse(eventId, activeToken));
                    continue;
                }

                responses.add(waitOrAdmit(eventId, userId, null));
            }

            return responses;
        });
    }

    /**
     * ticket-service가 전달한 active token이 해당 이벤트와 사용자에 대해 유효한지 검증한다.
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
     * active ZSet, active-token key, active-user 역방향 키를 함께 제거해 다음 사용자가 입장할 수 있게 한다.
     */
    @Observed(name = "queue.complete", contextualName = "queue complete")
    public boolean complete(Long eventId, String userId, String activeToken) {
        return executeWithRedisLogging("complete", eventId, userId, activeToken, () -> {
            validateUserId(userId);
            if (!isActiveTokenValid(eventId, userId, activeToken)) {
                return false;
            }

            redisTemplate.opsForZSet().remove(activeKey(eventId), activeToken);
            redisTemplate.delete(List.of(activeTokenKey(activeToken), activeUserKey(eventId, userId)));
            return true;
        });
    }

    /**
     * 스케줄러에서 active/waiting 만료 토큰을 주기적으로 정리한다.
     */
    public void cleanupExpiredTokens() {
        executeWithRedisLogging("cleanupExpiredTokens", null, null, null, () -> {
            scanEventIds("queue:event:*:active", ":active")
                    .forEach(this::pruneExpiredActiveTokens);
            scanEventIds("queue:event:*:waiting-expiry", ":waiting-expiry")
                    .forEach(this::pruneExpiredWaitingTokens);
            return null;
        });
    }

    /**
     * 대기열 페이지 이탈 시 waiting token을 회수한다.
     * active token은 좌석/결제 흐름에서 사용될 수 있으므로 여기서 회수하지 않는다.
     */
    @Observed(name = "queue.leave-waiting", contextualName = "queue leave waiting")
    public boolean leaveWaiting(Long eventId, String userId, String token) {
        return executeWithRedisLogging("leaveWaiting", eventId, userId, token, () -> {
            validateUserId(userId);
            if (!isWaitingTokenValid(eventId, userId, token)) {
                return false;
            }

            removeWaitingToken(eventId, userId, token);
            return true;
        });
    }

    /**
     * Redis 작업 중 발생한 DataAccessException을 공통 로그 포맷으로 남기고 다시 전파한다.
     */
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
     * 클라이언트가 제시한 active token이 유효하면 READY 응답을 만든다.
     * 토큰이 없거나 만료/불일치하면 null을 반환해 일반 대기열 흐름으로 넘긴다.
     */
    private QueueStatusResponse readyStatusIfValidActiveToken(Long eventId, String userId, String activeToken) {
        if (!isActiveTokenValid(eventId, userId, activeToken)) {
            return null;
        }

        return readyStatusResponse(eventId, activeToken);
    }

    /**
     * 사용자를 waiting queue에 등록/갱신한 뒤 active slot이 있으면 입장 토큰을 발급한다.
     * 입장 성공 시 READY, 실패 시 WAITING 응답을 반환한다.
     */
    private QueueStatusResponse waitOrAdmit(Long eventId, String userId, String clientToken) {
        String waitingToken = ensureWaiting(eventId, userId, clientToken);
        String activeToken = admit(eventId, userId, waitingToken);
        if (activeToken != null) {
            return readyStatusResponse(eventId, activeToken);
        }

        return waitingStatusResponse(eventId, userId, waitingToken);
    }

    /**
     * active token의 만료 시각을 기준으로 READY 응답 DTO를 만든다.
     */
    private QueueStatusResponse readyStatusResponse(Long eventId, String activeToken) {
        Double score = redisTemplate.opsForZSet().score(activeKey(eventId), activeToken);
        Long expiresAt = score == null ? null : score.longValue();
        Long expiresInSeconds = expiresAt == null ? null : Math.max(0L, (expiresAt - nowMillis()) / 1000);

        return new QueueStatusResponse(
                eventId,
                STATUS_READY,
                0L,
                redisTemplate.opsForZSet().zCard(waitingKey(eventId)),
                activeToken,
                expiresInSeconds
        );
    }

    /**
     * waiting queue에 등록된 사용자가 active slot에 들어갈 수 있는지 확인하고 가능하면 토큰을 발급한다.
     * Redis Lua script로 rank 확인, active slot 확인, token 저장, waiting 제거를 원자적으로 처리한다.
     */
    private String admit(Long eventId, String userId, String waitingToken) {
        String activeToken = UUID.randomUUID().toString();
        long expiresAt = nowMillis() + properties.getActiveTokenTtl().toMillis();
        String tokenValue = eventId + ":" + userId;

        Long admitted = redisTemplate.execute(
                ADMIT_IF_SLOT_AVAILABLE_SCRIPT,
                List.of(
                        waitingKey(eventId),
                        activeKey(eventId),
                        activeTokenKey(activeToken),
                        activeUserKey(eventId, userId),
                        waitingTokenKey(waitingToken),
                        waitingUserKey(eventId, userId),
                        waitingExpiryKey(eventId)
                ),
                waitingToken,
                activeToken,
                tokenValue,
                String.valueOf(expiresAt),
                String.valueOf(properties.getActiveTokenTtl().toMillis()),
                String.valueOf(properties.getAdmissionSize())
        );

        return Long.valueOf(1L).equals(admitted) ? activeToken : null;
    }

    /**
     * active token key 값과 active ZSet 존재 여부를 함께 확인해 active token의 유효성을 검증한다.
     */
    private boolean isActiveTokenValid(Long eventId, String userId, String activeToken) {
        if (!StringUtils.hasText(activeToken)) {
            return false;
        }

        String tokenValue = redisTemplate.opsForValue().get(activeTokenKey(activeToken));
        if (!(eventId + ":" + userId).equals(tokenValue)) {
            return false;
        }

        return redisTemplate.opsForZSet().score(activeKey(eventId), activeToken) != null;
    }

    /**
     * 사용자별 active-user 역방향 키로 active token을 조회한다.
     * 조회된 토큰은 실제 active ZSet에도 남아 있는지 다시 검증한다.
     */
    private Map<String, String> activeTokenByUserId(Long eventId, List<String> userIds) {
        Map<String, String> activeTokenByUserId = new HashMap<>();
        for (String userId : userIds) {
            String activeToken = redisTemplate.opsForValue().get(activeUserKey(eventId, userId));
            if (isActiveTokenValid(eventId, userId, activeToken)) {
                activeTokenByUserId.put(userId, activeToken);
            }
        }
        return activeTokenByUserId;
    }

    /**
     * 유효한 waiting token이 있으면 TTL만 갱신하고, 없으면 일회용 waiting token을 새로 등록한다.
     * waiting ZSet score는 최초 진입 순서 보존용이므로 갱신하지 않는다.
     */
    private String ensureWaiting(Long eventId, String userId, String clientToken) {
        String waitingToken = resolveWaitingToken(eventId, userId, clientToken);
        if (waitingToken != null) {
            refreshWaitingToken(eventId, userId, waitingToken);
            return waitingToken;
        }

        removeCurrentWaitingTokenIfExists(eventId, userId);
        waitingToken = UUID.randomUUID().toString();
        redisTemplate.opsForZSet().add(waitingKey(eventId), waitingToken, nowMillis());
        refreshWaitingToken(eventId, userId, waitingToken);
        return waitingToken;
    }

    /**
     * waiting queue의 현재 rank와 전체 대기 수를 기준으로 WAITING 응답 DTO를 만든다.
     */
    private QueueStatusResponse waitingStatusResponse(Long eventId, String userId, String waitingToken) {
        Long rank = redisTemplate.opsForZSet().rank(waitingKey(eventId), waitingToken);
        return new QueueStatusResponse(
                eventId,
                STATUS_WAITING,
                rank == null ? null : rank + 1,
                redisTemplate.opsForZSet().zCard(waitingKey(eventId)),
                waitingToken,
                properties.getWaitingTokenTtl().toSeconds()
        );
    }

    /**
     * 클라이언트가 제시한 토큰이 유효한 waiting token이면 그대로 사용한다.
     * 토큰이 없거나 유효하지 않으면 null을 반환해 새 waiting token 발급 흐름으로 보낸다.
     */
    private String resolveWaitingToken(Long eventId, String userId, String clientToken) {
        if (isWaitingTokenValid(eventId, userId, clientToken)) {
            return clientToken;
        }

        return null;
    }

    /**
     * waiting token의 소유자(eventId:userId)와 waiting ZSet 존재 여부를 함께 검증한다.
     */
    private boolean isWaitingTokenValid(Long eventId, String userId, String waitingToken) {
        if (!StringUtils.hasText(waitingToken)) {
            return false;
        }

        String tokenValue = redisTemplate.opsForValue().get(waitingTokenKey(waitingToken));
        if (!(eventId + ":" + userId).equals(tokenValue)) {
            return false;
        }

        return redisTemplate.opsForZSet().score(waitingKey(eventId), waitingToken) != null;
    }

    /**
     * waiting token의 TTL을 갱신하고, 스케줄러 정리를 위한 만료 인덱스 ZSet score도 갱신한다.
     */
    private void refreshWaitingToken(Long eventId, String userId, String waitingToken) {
        String tokenValue = eventId + ":" + userId;
        redisTemplate.opsForValue().set(waitingTokenKey(waitingToken), tokenValue, properties.getWaitingTokenTtl());
        redisTemplate.opsForValue().set(waitingUserKey(eventId, userId), waitingToken, properties.getWaitingTokenTtl());
        redisTemplate.opsForZSet().add(
                waitingExpiryKey(eventId),
                waitingToken,
                nowMillis() + properties.getWaitingTokenTtl().toMillis()
        );
    }

    /**
     * 같은 사용자의 기존 waiting token이 남아 있으면 새 대기열 등록 전에 제거한다.
     */
    private void removeCurrentWaitingTokenIfExists(Long eventId, String userId) {
        String currentWaitingToken = redisTemplate.opsForValue().get(waitingUserKey(eventId, userId));
        if (StringUtils.hasText(currentWaitingToken)) {
            removeWaitingToken(eventId, userId, currentWaitingToken);
        }
    }

    /**
     * waiting queue, 만료 인덱스, token/user 역방향 key에서 waiting token을 제거한다.
     */
    private void removeWaitingToken(Long eventId, String userId, String waitingToken) {
        redisTemplate.opsForZSet().remove(waitingKey(eventId), waitingToken);
        redisTemplate.opsForZSet().remove(waitingExpiryKey(eventId), waitingToken);
        redisTemplate.delete(List.of(waitingTokenKey(waitingToken), waitingUserKey(eventId, userId)));
    }

    /**
     * waiting-expiry ZSet에서 만료 시각이 지난 waiting token을 찾아 waiting queue에서 제거한다.
     */
    private void pruneExpiredWaitingTokens(Long eventId) {
        Set<String> waitingTokens = redisTemplate.opsForZSet().rangeByScore(waitingExpiryKey(eventId), 0, nowMillis());
        if (waitingTokens == null || waitingTokens.isEmpty()) {
            return;
        }

        for (String waitingToken : waitingTokens) {
            redisTemplate.opsForZSet().remove(waitingKey(eventId), waitingToken);
            redisTemplate.opsForZSet().remove(waitingExpiryKey(eventId), waitingToken);
            redisTemplate.delete(waitingTokenKey(waitingToken));
        }
    }

    /**
     * active ZSet에 남아 있는 만료 토큰을 제거한다.
     * active-token key와 active-user 역방향 키도 함께 정리한다.
     */
    private void pruneExpiredActiveTokens(Long eventId) {
        String key = activeKey(eventId);
        Set<String> expiredTokens = redisTemplate.opsForZSet().rangeByScore(key, 0, nowMillis());
        if (expiredTokens == null || expiredTokens.isEmpty()) {
            return;
        }

        redisTemplate.opsForZSet().removeRangeByScore(key, 0, nowMillis());
        for (String activeToken : expiredTokens) {
            String tokenValue = redisTemplate.opsForValue().get(activeTokenKey(activeToken));
            redisTemplate.delete(activeTokenKey(activeToken));
            deleteActiveUserKey(tokenValue);
        }
    }

    /**
     * Redis key scan으로 cleanup 대상 이벤트 ID 목록을 추출한다.
     */
    private Set<Long> scanEventIds(String pattern, String suffix) {
        Set<String> keys = redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> scannedKeys = new HashSet<>();
            ScanOptions options = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(properties.getCleanupScanCount())
                    .build();

            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    scannedKeys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }

            return scannedKeys;
        });

        if (keys == null || keys.isEmpty()) {
            return Set.of();
        }

        return keys.stream()
                .map(key -> parseEventIdFromQueueEventKey(key, suffix))
                .filter(eventId -> eventId != null)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * queue:event:{eventId}:{suffix} 형태의 Redis key에서 eventId를 파싱한다.
     */
    private Long parseEventIdFromQueueEventKey(String key, String suffix) {
        String prefix = "queue:event:";
        if (!StringUtils.hasText(key) || !key.startsWith(prefix) || !key.endsWith(suffix)) {
            return null;
        }

        String eventId = key.substring(prefix.length(), key.length() - suffix.length());
        try {
            return Long.valueOf(eventId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * active token value(eventId:userId)를 파싱해 active-user 역방향 키를 제거한다.
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
     * [WAITING] 이벤트별 waiting queue ZSet key를 만든다.
     * member는 waiting token, score는 최초 대기열 진입 시각이다.
     */
    private String waitingKey(Long eventId) {
        return "queue:event:" + eventId + ":waiting";
    }

    /**
     * [WAITING] waiting token 문자열로 대기열 사용자 정보(eventId:userId)를 찾는 key를 만든다.
     * value는 eventId:userId, TTL은 waiting token TTL이다.
     */
    private String waitingTokenKey(String token) {
        return "queue:waiting-token:" + token;
    }

    /**
     * [WAITING] 이벤트와 사용자 기준으로 현재 유효한 waiting token을 직접 조회하는 역방향 key를 만든다.
     * value는 waiting token, TTL은 waiting token TTL이다.
     */
    private String waitingUserKey(Long eventId, String userId) {
        return "queue:waiting-user:" + eventId + ":" + userId;
    }

    /**
     * [WAITING] 이벤트별 waiting token 만료 시각을 추적하는 ZSet key를 만든다.
     * member는 waiting token, score는 waiting token 만료 시각이다.
     */
    private String waitingExpiryKey(Long eventId) {
        return "queue:event:" + eventId + ":waiting-expiry";
    }

    /**
     * [ACTIVE] 이벤트별 active token ZSet key를 만든다.
     * member는 active token, score는 active token 만료 시각이다.
     */
    private String activeKey(Long eventId) {
        return "queue:event:" + eventId + ":active";
    }

    /**
     * [ACTIVE] active token 문자열로 token value(eventId:userId)를 찾는 key를 만든다.
     * value는 eventId:userId, TTL은 active token TTL이다.
     */
    private String activeTokenKey(String activeToken) {
        return "queue:active-token:" + activeToken;
    }

    /**
     * [ACTIVE] 이벤트와 사용자 기준으로 active token을 직접 조회하는 역방향 key를 만든다.
     * value는 active token, TTL은 active token TTL이다.
     */
    private String activeUserKey(Long eventId, String userId) {
        return "queue:active-user:" + eventId + ":" + userId;
    }

    /*
    eventId -> 1
    userId -> IU
    waitingToken -> 930516
    이라고 가정

    1. 대기열 등록 시

    - queue:event:1:waiting (key) (타입 : zSet)
      - 930516 (value) - 최초 진입 시각 (score)

    - queue:waiting-token:930516 (key) (타입 : set)
      - 1:IU (value) - 1분 (TTL)

    - queue:waiting-user:1:IU (key) (타입 : set)
      - 930516 (value) - 1분 (TTL)

    - queue:event:1:waiting-expiry (key) (타입 : zSet)
      - 930516 -> 만료 시각 (score)

     2. READY 승격 시:

     - queue:event:1:active
        activeToken -> 만료 시각

     - queue:active-token:activeTokenB
        "1:user01"

     - queue:active-user:1:user01
        "activeTokenB"
     */
}
