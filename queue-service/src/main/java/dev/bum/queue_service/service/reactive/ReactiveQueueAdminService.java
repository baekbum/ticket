package dev.bum.queue_service.service.reactive;

import dev.bum.common.service.queue.dto.QueueStatusResponse;
import dev.bum.queue_service.exception.QueueTokenInvalidException;
import io.micrometer.observation.ObservationRegistry;
import reactor.core.observability.micrometer.Micrometer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReactiveQueueAdminService {
    private final ObservationRegistry observationRegistry;
    private final ReactiveWaitingQueueService waiting;
    private final ReactiveActiveQueueService active;
    private final ReactiveUserQueueSessionService sessions;

    public Mono<List<QueueStatusResponse>> statuses(Long eventId, List<String> userIds, Map<String, String> tokens) {
        return Mono.defer(() -> {
            userIds.forEach(userId -> {
                if (!StringUtils.hasText(userId)) throw new IllegalArgumentException("인증 정보가 올바르지 않습니다.");
            });
            return Flux.fromIterable(userIds)
                    .concatMap(sessions::prune)
                    .then(active.activeTokenByUserId(eventId, userIds))
                    .flatMap(activeTokens -> Flux.fromIterable(userIds)
                            .concatMap(userId -> {
                                String token = activeTokens.get(userId);
                                if (token != null) return active.readyStatusResponse(eventId, token);
                                return waitingStatus(eventId, userId, tokens == null ? null : tokens.get(userId));
                            })
                            .collectList());
        })
                .doOnError(DataAccessException.class, error ->
                        log.error("[REDIS-ERROR] 관리자 대기열 조회 실패. eventId={}", eventId, error))
                .name("queue.admin.statuses")
                .tap(Micrometer.observation(observationRegistry));
    }

    private Mono<QueueStatusResponse> waitingStatus(Long eventId, String userId, String token) {
        return active.admit(eventId, userId, token)
                .flatMap(activeToken -> active.readyStatusResponse(eventId, activeToken))
                .switchIfEmpty(Mono.defer(() -> waiting.waitingStatusResponse(eventId, userId, token)));
    }
}