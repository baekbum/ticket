package dev.bum.queue_service.service.reactive;

import dev.bum.common.service.queue.dto.*;
import dev.bum.queue_service.config.QueueProperties;
import dev.bum.queue_service.service.QueueSessionType;
import io.micrometer.observation.ObservationRegistry;
import reactor.core.observability.micrometer.Micrometer;
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

    // 동시 대기열 세션 제한의 확인이 필요한 상태 값.
    private static final String STATUS_SESSION_LIMIT_CONFIRM_REQUIRED = "SESSION_LIMIT_CONFIRM_REQUIRED";
    // 대기열 상태와 토큰을 저장하고 조회하는 Redis 클라이언트.
    private final ReactiveStringRedisTemplate redis;
    // 서비스 작업의 관측 정보를 기록하는 레지스트리.
    private final ObservationRegistry observationRegistry;
    // 처리에 사용할 설정.
    private final QueueProperties properties;
    // 대기열 대기 업무 처리를 담당하는 서비스.
    private final ReactiveWaitingQueueService waiting;
    // 활성화 여부.
    private final ReactiveActiveQueueService active;
    // 사용자 업무 처리를 담당하는 서비스.
    private final ReactiveUserQueueSessionService sessions;
    // 공연 정보를 저장하고 조회하는 도구.
    private final EventQueuePolicyStore policies;

    /**
     * 공연 대기열에 사용자를 등록하고 입장 상태를 반환한다.
     */
    public Mono<QueueEnterResponse> enter(Long eventId, String userId, boolean force) {

        return logged("enter", eventId, userId, () -> {
            validateUserId(userId);

            return policies.validate(eventId, false).then(cleanupEventQueue(eventId))
                    .then(sessions.prune(userId))
                    .then(sessions.count(userId))
                    .flatMap(count -> {
                        if (count >= properties.getMaxSessionsPerUser()) {
                            if (!force) return sessionLimitResponse(eventId);
                            return removeFirstExpiringSession(userId)
                                    .then(createWaiting(eventId, userId));
                        }

                        return createWaiting(eventId, userId);
                    }).map(QueueStatusResponse::toEnterResponse);
        });
    }

    /**
     * 현재 처리 상태를 조회한다.
     */
    public Mono<QueueStatusResponse> status(Long eventId, String userId, String waitingToken) {

        return logged("status", eventId, userId, () -> {
            validateUserId(userId);
            // 대기 토큰 삭제 여부보다 Lua의 기존 승격 매핑·소유권 판단을 우선한다.
            return cleanupEventQueue(eventId)
                    .then(Mono.defer(() -> active.admit(eventId, userId, waitingToken)
                            .flatMap(token -> active.readyStatusResponse(eventId, token))
                            .switchIfEmpty(Mono.defer(() -> waiting.waitingStatusResponse(eventId, waitingToken)))));
        });
    }

    /**
     * 대기열 처리에서 완료 상태로 변경하고 결과를 반영한다.
     */
    public Mono<Boolean> complete(Long eventId, String userId, String token) {

        return logged("complete", eventId, userId, () -> {
            validateUserId(userId);
            return active.isActiveTokenValid(eventId, userId, token)
                    .flatMap(valid -> valid ? active.removeActiveToken(eventId, userId, token) : Mono.just(false));
        });
    }

    /**
     * 사용자를 대기열에서 제거하고 세션을 정리한다.
     */
    public Mono<Boolean> leave(Long eventId, String userId, String waitingToken, String activeToken) {

        return logged("leave", eventId, userId, () -> {
            validateUserId(userId);
            return waiting.isWaitingTokenValid(eventId, userId, waitingToken)
                    .flatMap(valid -> {
                        if (valid) return waiting.removeWaitingToken(eventId, userId, waitingToken)
                                .thenReturn(true);
                        return active.isActiveTokenValid(eventId, userId, activeToken)
                                .flatMap(activeValid -> activeValid
                                        ? active.removeActiveToken(eventId, userId, activeToken) : Mono.just(false));
                    });
        });
    }

    /**
     * 만료된 대기 토큰, 입장 토큰과 세션을 정리한다.
     */
    public Mono<Void> cleanupExpiredTokens() {

        return logged("cleanupExpiredTokens", null, null, () ->
                scanEventIds("queue:event:*:waiting-expiry", ":waiting-expiry")
                        .concatMap(waiting::pruneExpiredWaitingTokens)
                        .thenMany(scanEventIds("queue:event:*:active", ":active")
                                .concatMap(active::pruneExpiredActiveTokens))
                        .then(Mono.defer(() -> sessions.prunePersistentIndexes(properties.getCleanupScanCount()))));
    }

    /**
     * 대기열 처리에서 요청 값과 현재 상태가 유효한지 검증한다.
     */
    public Mono<QueueValidateResponse> validate(QueueValidateRequest request) {

        return logged("validate", request.eventId(), request.userId(),
                () -> policies.validate(request.eventId(), true)
                        .then(active.isActiveTokenValid(request.eventId(), request.userId(), request.token()))
                        .map(valid -> new QueueValidateResponse(valid, valid ? "OK" : "INVALID_QUEUE_TOKEN")));
    }

    /**
     * 대기 토큰과 사용자 세션을 생성한다.
     */
    private Mono<QueueStatusResponse> createWaiting(Long eventId, String userId) {

        return waiting.createWaiting(eventId, userId)
                .flatMap(token -> waiting.waitingStatusResponse(eventId, token))
                .switchIfEmpty(Mono.defer(() -> sessionLimitResponse(eventId)));
    }

    /**
     * 가장 먼저 만료될 사용자 세션을 제거한다.
     */
    private Mono<Void> removeFirstExpiringSession(String userId) {

        return sessions.firstExpiringSession(userId)
                .flatMap(session -> sessions.eventIdForSession(userId, session)
                        .flatMap(originalEventId -> (session.type() == QueueSessionType.WAITING
                                ? waiting.removeWaitingToken(originalEventId, userId, session.token())
                                : active.removeActiveToken(originalEventId, userId, session.token())
                                        .then())
                                .thenReturn(true))
                        .switchIfEmpty(sessions.prune(userId)
                                .thenReturn(false)))
                .then();
    }

    /**
     * 동시 대기열 세션 제한에 대한 응답을 구성한다.
     */
    private Mono<QueueStatusResponse> sessionLimitResponse(Long eventId) {

        return waiting.waitingCount(eventId)
                .map(count -> new QueueStatusResponse(
                        eventId, STATUS_SESSION_LIMIT_CONFIRM_REQUIRED, null, count, null, null, null, null));
    }

    /**
     * 지정한 공연의 만료된 대기열 정보를 정리한다.
     */
    private Mono<Void> cleanupEventQueue(Long eventId) {

        return waiting.pruneExpiredWaitingTokens(eventId)
                .then(active.pruneExpiredActiveTokens(eventId));
    }

    /**
     * Redis 키를 순회하며 공연 식별자를 추출한다.
     */
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

    /**
     * 대기열 작업을 실행하고 관측 및 오류 정보를 기록한다.
     */
    private <T> Mono<T> logged(String operation, Long eventId, String userId, Supplier<Mono<T>> action) {

        // 구독 시 발생하는 Redis 오류까지 기록하고 호출자에게 그대로 전달한다.
        return Mono.defer(action)
                .doOnError(DataAccessException.class, error ->
                        log.error("[REDIS-ERROR] Queue Redis 처리 실패. operation={}, eventId={}, userId={}", operation, eventId, userId, error)
                )
                // Mono 생성 시간이 아니라 구독부터 완료·오류·취소까지 관측한다.
                .name("queue." + ("validate".equals(operation) ? "validate-token" : operation))
                .tap(Micrometer.observation(observationRegistry));
    }

    /**
     * 사용자 ID가 유효한지 검증한다.
     */
    private void validateUserId(String userId) {

        if (!StringUtils.hasText(userId)) throw new IllegalArgumentException("인증 정보가 올바르지 않습니다.");
    }
}
