package dev.bum.queue_service.service;

import dev.bum.common.service.queue.dto.QueueEnterResponse;
import dev.bum.common.service.queue.dto.QueueStatusResponse;
import dev.bum.common.service.queue.dto.QueueValidateRequest;
import dev.bum.common.service.queue.dto.QueueValidateResponse;
import dev.bum.queue_service.config.QueueProperties;
import dev.bum.queue_service.exception.QueueTokenInvalidException;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private static final String STATUS_SESSION_LIMIT_CONFIRM_REQUIRED = "SESSION_LIMIT_CONFIRM_REQUIRED";

    private final StringRedisTemplate redisTemplate;
    private final QueueProperties properties;
    private final WaitingQueueService waitingQueueService;
    private final ActiveQueueService activeQueueService;
    private final UserQueueSessionService userQueueSessionService;

    /**
     * 사용자의 대기열 최초 진입 요청을 처리한다.
     * 사용자별 세션 제한을 확인한 뒤 waiting token을 발급한다.
     */
    @Observed(name = "queue.enter", contextualName = "queue enter")
    public QueueEnterResponse enter(Long eventId, String userId, boolean force) {
        return executeWithRedisLogging("enter", eventId, userId, null, () -> {
            validateUserId(userId);

            return enterWaiting(eventId, userId, force).toEnterResponse();
        });
    }

    /**
     * 사용자의 현재 대기열 상태를 조회한다.
     * 유효한 active token이 있으면 READY를 반환하고, 없으면 대기열 기준으로 입장을 다시 시도한다.
     */
    @Observed(name = "queue.status", contextualName = "queue status")
    public QueueStatusResponse status(Long eventId, String userId, String waitingToken) {
        return executeWithRedisLogging("status", eventId, userId, waitingToken, () -> {
            validateUserId(userId);

            return admitWaitingToken(eventId, userId, waitingToken);
        });
    }

    /**
     * 여러 사용자의 대기열 상태를 한 번에 조회한다.
     * active-user 역방향 키를 먼저 확인하고, READY가 아니면 사용자별로 입장을 시도한다.
     */
    @Observed(name = "queue.statuses", contextualName = "queue bulk statuses")
    public List<QueueStatusResponse> statuses(Long eventId, List<String> userIds, Map<String, String> tokenByUserId) {
        return executeWithRedisLogging("statuses", eventId, String.join(",", userIds), null, () -> {
            userIds.forEach(userQueueSessionService::prune);
            Map<String, String> activeTokenByUserId = activeQueueService.activeTokenByUserId(eventId, userIds);
            List<QueueStatusResponse> responses = new ArrayList<>();

            for (String userId : userIds) {
                validateUserId(userId);
                String waitingToken = tokenByUserId == null ? null : tokenByUserId.get(userId);

                String activeToken = activeTokenByUserId.get(userId);
                if (activeToken != null) {
                    responses.add(activeQueueService.readyStatusResponse(eventId, activeToken));
                    continue;
                }

                responses.add(admitWaitingToken(eventId, userId, waitingToken));
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
            boolean valid = activeQueueService.isActiveTokenValid(request.eventId(), request.userId(), request.token());
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
            if (!activeQueueService.isActiveTokenValid(eventId, userId, activeToken)) {
                return false;
            }

            return activeQueueService.removeActiveToken(eventId, userId, activeToken);
        });
    }

    /**
     * 스케줄러에서 active/waiting 만료 토큰을 주기적으로 정리한다.
     */
    public void cleanupExpiredTokens() {
        executeWithRedisLogging("cleanupExpiredTokens", null, null, null, () -> {

            scanEventIds("queue:event:*:waiting-expiry", ":waiting-expiry")
                    .forEach(waitingQueueService::pruneExpiredWaitingTokens);

            scanEventIds("queue:event:*:active", ":active")
                    .forEach(activeQueueService::pruneExpiredActiveTokens);

            return null;
        });
    }

    /**
     * 대기열 또는 예매창 이탈 시 waiting/active token을 회수한다.
     */
    @Observed(name = "queue.leave", contextualName = "queue leave")
    public boolean leave(Long eventId, String userId, String waitingToken, String activeToken) {
        String tokenForLog = StringUtils.hasText(waitingToken) ? waitingToken : activeToken;
        return executeWithRedisLogging("leave", eventId, userId, tokenForLog, () -> {
            validateUserId(userId);

            if (waitingQueueService.isWaitingTokenValid(eventId, userId, waitingToken)) {
                waitingQueueService.removeWaitingToken(eventId, userId, waitingToken);
                return true;
            }

            if (activeQueueService.isActiveTokenValid(eventId, userId, activeToken)) {
                return activeQueueService.removeActiveToken(eventId, userId, activeToken);
            }

            return false;
        });
    }

    private QueueStatusResponse enterWaiting(Long eventId, String userId, boolean force) {
        cleanupEventQueue(eventId, userId);
        userQueueSessionService.prune(userId);

        // 접속할 수 있는 최대 세션을 초과하는지 확인하는 로직
        if (isUserSessionLimitReached(userId)) {
            if (!force) {
                return sessionLimitConfirmRequiredResponse(eventId); // 접속할 지 안할지 응답을 리턴
            }

            removeFirstExpiringUserSession(eventId, userId); // 만료 시간이 가장 빠른 waiting 토큰 삭제
        }

        String waitingToken = waitingQueueService.createWaiting(eventId, userId);
        return waitingQueueService.waitingStatusResponse(eventId, userId, waitingToken);
    }

    private QueueStatusResponse admitWaitingToken(Long eventId, String userId, String waitingToken) {
        cleanupEventQueue(eventId, userId);

        if (!waitingQueueService.isWaitingTokenValid(eventId, userId, waitingToken)) {
            throw new QueueTokenInvalidException("올바르지 않은 시도입니다. 다시 시도해주세요.");
        }

        String activeToken = activeQueueService.admit(eventId, userId, waitingToken);
        if (activeToken != null) { // 현재 진입할 수 있는 상태라면 active 토큰을 발행하여 응답
            return activeQueueService.readyStatusResponse(eventId, activeToken);
        }

        waitingQueueService.refreshWaitingToken(eventId, userId, waitingToken);

        return waitingQueueService.waitingStatusResponse(eventId, userId, waitingToken);
    }

    private boolean isUserSessionLimitReached(String userId) {
        return userQueueSessionService.count(userId) >= properties.getMaxSessionsPerUser();
    }

    private void removeFirstExpiringUserSession(Long eventId, String userId) {
        QueueUserSession session = userQueueSessionService.firstExpiringSession(userId);
        if (session == null) {
            return;
        }

        if (session.type() == QueueSessionType.WAITING) {
            waitingQueueService.removeWaitingToken(eventId, userId, session.token());
            return;
        }

        activeQueueService.removeActiveToken(eventId, userId, session.token());
    }

    private QueueStatusResponse sessionLimitConfirmRequiredResponse(Long eventId) {
        return new QueueStatusResponse(
                eventId,
                STATUS_SESSION_LIMIT_CONFIRM_REQUIRED,
                null,
                waitingQueueService.waitingCount(eventId),
                null,
                null,
                null,
                null
        );
    }

    /**
     * 만료된 waiting, active 토큰에 대한 관련된 모든 토큰을 제거한다.
     * @param eventId
     * @param userId
     */
    private void cleanupEventQueue(Long eventId, String userId) {
        waitingQueueService.pruneExpiredWaitingTokens(eventId, userId);
        activeQueueService.pruneExpiredActiveTokens(eventId);
    }

    private Set<Long> scanEventIds(String pattern, String suffix) {
        Set<String> scannedKeys = redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> result = new HashSet<>();
            ScanOptions options = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(properties.getCleanupScanCount())
                    .build();

            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    result.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }

            return result;
        });

        if (scannedKeys == null || scannedKeys.isEmpty()) {
            return Set.of();
        }

        return scannedKeys.stream()
                .map(key -> parseEventIdFromQueueEventKey(key, suffix))
                .filter(eventId -> eventId != null)
                .collect(java.util.stream.Collectors.toSet());
    }

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

    private void validateUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            log.info("Invalid user id : {}", userId);
            throw new IllegalArgumentException("인증 정보가 올바르지 않습니다.");
        }
    }
}
