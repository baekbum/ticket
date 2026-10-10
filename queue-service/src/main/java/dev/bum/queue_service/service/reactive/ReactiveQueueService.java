package dev.bum.queue_service.service.reactive;

import dev.bum.common.service.queue.dto.*;
import dev.bum.queue_service.config.QueueProperties;
import dev.bum.queue_service.exception.QueueTokenInvalidException;
import dev.bum.queue_service.service.QueueSessionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReactiveQueueService {
    private static final String STATUS_SESSION_LIMIT_CONFIRM_REQUIRED = "SESSION_LIMIT_CONFIRM_REQUIRED";
    private final ReactiveStringRedisTemplate redis;
    private final QueueProperties properties;
    private final ReactiveWaitingQueueService waiting;
    private final ReactiveActiveQueueService active;
    private final ReactiveUserQueueSessionService sessions;

    public Mono<QueueEnterResponse> enter(Long eventId, String userId, boolean force) {
        return logged("enter", eventId, userId, () -> {
            validateUserId(userId);
            return cleanupEventQueue(eventId, userId).then(sessions.prune(userId))
                    .then(sessions.count(userId)).flatMap(count -> {
                        if (count >= properties.getMaxSessionsPerUser()) {
                            if (!force) return sessionLimitResponse(eventId);
                            return removeFirstExpiringSession(eventId, userId).then(createWaiting(eventId, userId));
                        }
                        return createWaiting(eventId, userId);
                    }).map(QueueStatusResponse::toEnterResponse);
        });
    }

    public Mono<QueueStatusResponse> status(Long eventId, String userId, String waitingToken) {
        return logged("status", eventId, userId, () -> {
            validateUserId(userId);
            return cleanupEventQueue(eventId, userId)
                    .then(waiting.isWaitingTokenValid(eventId, userId, waitingToken))
                    .flatMap(valid -> valid ? active.admit(eventId, userId, waitingToken)
                            .flatMap(token -> active.readyStatusResponse(eventId, token))
                            .switchIfEmpty(Mono.defer(() -> waiting.refreshWaitingToken(eventId, userId, waitingToken)
                                    .then(waiting.waitingStatusResponse(eventId, userId, waitingToken))))
                            : Mono.error(invalidToken()));
        });
    }

    public Mono<QueueValidateResponse> validate(QueueValidateRequest request) {
        return logged("validate", request.eventId(), request.userId(),
                () -> active.isActiveTokenValid(request.eventId(), request.userId(), request.token())
                        .map(valid -> new QueueValidateResponse(valid, valid ? "OK" : "INVALID_QUEUE_TOKEN")));
    }

    public Mono<Boolean> complete(Long eventId, String userId, String token) {
        return logged("complete", eventId, userId, () -> {
            validateUserId(userId);
            return active.isActiveTokenValid(eventId, userId, token)
                    .flatMap(valid -> valid ? active.removeActiveToken(eventId, userId, token) : Mono.just(false));
        });
    }

    public Mono<Boolean> leave(Long eventId, String userId, String waitingToken, String activeToken) {
        return logged("leave", eventId, userId, () -> {
            validateUserId(userId);
            return waiting.isWaitingTokenValid(eventId, userId, waitingToken).flatMap(valid -> {
                if (valid) return waiting.removeWaitingToken(eventId, userId, waitingToken).thenReturn(true);
                return active.isActiveTokenValid(eventId, userId, activeToken)
                        .flatMap(activeValid -> activeValid
                                ? active.removeActiveToken(eventId, userId, activeToken) : Mono.just(false));
            });
        });
    }

    public Mono<Void> cleanupExpiredTokens() {
        return logged("cleanupExpiredTokens", null, null, () ->
                scanEventIds("queue:event:*:waiting-expiry", ":waiting-expiry")
                        .concatMap(waiting::pruneExpiredWaitingTokens)
                        .thenMany(scanEventIds("queue:event:*:active", ":active")
                                .concatMap(active::pruneExpiredActiveTokens)).then());
    }

    private Mono<QueueStatusResponse> createWaiting(Long eventId, String userId) {
        return waiting.createWaiting(eventId, userId).flatMap(token -> waiting.waitingStatusResponse(eventId, userId, token));
    }

    private Mono<Void> removeFirstExpiringSession(Long eventId, String userId) {
        return sessions.firstExpiringSession(userId).flatMap(session ->
                session.type() == QueueSessionType.WAITING
                        ? waiting.removeWaitingToken(eventId, userId, session.token())
                        : active.removeActiveToken(eventId, userId, session.token()).then());
    }

    private Mono<QueueStatusResponse> sessionLimitResponse(Long eventId) {
        return waiting.waitingCount(eventId).map(count -> new QueueStatusResponse(
                eventId, STATUS_SESSION_LIMIT_CONFIRM_REQUIRED, null, count, null, null, null, null));
    }

    private Mono<Void> cleanupEventQueue(Long eventId, String userId) {
        return waiting.pruneExpiredWaitingTokens(eventId, userId).then(active.pruneExpiredActiveTokens(eventId));
    }

    private Flux<Long> scanEventIds(String pattern, String suffix) {
        return redis.scan(ScanOptions.scanOptions().match(pattern).count(properties.getCleanupScanCount()).build())
                .<Long>handle((key, sink) -> {
                    String prefix = "queue:event:";
                    if (!key.startsWith(prefix) || !key.endsWith(suffix)) return;
                    try {
                        sink.next(Long.valueOf(key.substring(prefix.length(), key.length() - suffix.length())));
                    } catch (NumberFormatException ignored) {
                        // 이벤트 ID 형식이 아닌 키는 기존 정리 로직과 같이 건너뛴다.
                    }
                }).distinct();
    }

    private <T> Mono<T> logged(String operation, Long eventId, String userId, Supplier<Mono<T>> action) {
        // 구독 시 발생하는 Redis 오류까지 기록하고 호출자에게 그대로 전달한다.
        return Mono.defer(action).doOnError(DataAccessException.class, error ->
                log.error("[REDIS-ERROR] Queue Redis 처리 실패. operation={}, eventId={}, userId={}",
                        operation, eventId, userId, error));
    }

    private QueueTokenInvalidException invalidToken() {
        return new QueueTokenInvalidException("올바르지 않은 시도입니다. 다시 시도해주세요.");
    }

    private void validateUserId(String userId) {
        if (!StringUtils.hasText(userId)) throw new IllegalArgumentException("인증 정보가 올바르지 않습니다.");
    }
}
