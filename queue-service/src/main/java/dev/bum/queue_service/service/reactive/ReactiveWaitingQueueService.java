package dev.bum.queue_service.service.reactive;

import dev.bum.common.service.queue.dto.QueueStatusResponse;
import dev.bum.queue_service.config.QueueProperties;
import dev.bum.queue_service.exception.EventPolicyException;
import dev.bum.queue_service.service.QueueRedisKeys;
import dev.bum.queue_service.service.QueueRedisScripts;
import dev.bum.queue_service.exception.QueueTokenInvalidException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReactiveWaitingQueueService {

    // 입장을 기다리는 대기열 상태 값.
    public static final String STATUS_WAITING = "WAITING";
    // 대기열 상태와 토큰을 저장하고 조회하는 Redis 클라이언트.
    private final ReactiveStringRedisTemplate redis;
    // 처리에 사용할 설정.
    private final QueueProperties properties;
    // 대기열 작업에 사용할 Redis 키 생성 도구.
    private final QueueRedisKeys keys;
    // 활성화 여부.
    private final ReactiveActiveQueueService active;
    // 사용자 업무 처리를 담당하는 서비스.
    private final ReactiveUserQueueSessionService sessions;

    /** 세션 한도에 도달하면 빈 Mono로 완료한다. */
    public Mono<String> createWaiting(Long eventId, String userId) {

        return Mono.defer(() -> {
            String token = UUID.randomUUID().toString();
            long now = nowMillis();
            long ttl = properties.getWaitingTokenTtl().toMillis();
            return redis.execute(QueueRedisScripts.REGISTER_WAITING,
                            List.of(keys.waitingKey(eventId), keys.waitingExpiryKey(eventId),
                                    keys.waitingTokenKey(token), keys.userSessionsKey(userId), keys.policyKey(eventId)),
                            List.of(token, tokenValue(eventId, userId), String.valueOf(now),
                                    String.valueOf(ttl), String.valueOf(properties.getMaxSessionsPerUser()),
                                    keys.waitingSessionMember(token)))
                    .next()
                    .flatMap(result -> result < 0 ? Mono.error(new EventPolicyException(
                            result == -2 ? "POLICY_UNAVAILABLE" : "EVENT_NOT_BOOKABLE")) : Mono.just(result))
                    .filter(result -> result == 1L)
                    .map(result -> token);
        });
    }

    /**
     * 대기 토큰의 소유자와 유효 기간을 확인한다.
     */
    public Mono<Boolean> isWaitingTokenValid(Long eventId, String userId, String token) {

        return Mono.defer(() -> {
            if (!StringUtils.hasText(token)) return Mono.just(false);
            return redis.opsForValue().get(keys.waitingTokenKey(token))
                    .filter(value -> tokenValue(eventId, userId).equals(value))
                    .flatMap(value -> redis.opsForZSet().score(keys.waitingKey(eventId), token)
                            .hasElement())
                    .defaultIfEmpty(false);
        });
    }

    /**
     * 대기 토큰과 세션의 유효 시간을 갱신한다.
     */
    public Mono<Void> refreshWaitingToken(Long eventId, String userId, String token) {

        return Mono.defer(() -> {
            long ttl = properties.getWaitingTokenTtl().toMillis();
            return redis.execute(QueueRedisScripts.REFRESH_WAITING, waitingKeys(eventId, userId, token),
                            List.of(token, tokenValue(eventId, userId), String.valueOf(ttl), keys.waitingSessionMember(token)))
                    .next()
                    .filter(result -> result == 1L)
                    .switchIfEmpty(Mono.error(new QueueTokenInvalidException("대기 토큰이 만료되었거나 이미 처리되었습니다.")))
                    .then();
        });
    }

    /**
     * 대기 토큰과 관련 세션 정보를 제거한다.
     */
    public Mono<Void> removeWaitingToken(Long eventId, String userId, String token) {

        return removeWaiting(eventId, userId, token, -1L);
    }

    /**
     * 만료된 대기 토큰과 연결 정보를 정리한다.
     */
    public Mono<Void> pruneExpiredWaitingTokens(Long eventId) {

        return Mono.defer(() -> {
            long cutoff = nowMillis();
            return redis.opsForZSet().rangeByScore(keys.waitingExpiryKey(eventId), Range.closed(0.0, (double) cutoff))
                    .concatMap(token -> redis.opsForValue().get(keys.waitingTokenKey(token))
                            .map(value -> value.split(":", 2))
                            .filter(parts -> parts.length == 2)
                            .map(parts -> Optional.of(parts[1]))
                            .defaultIfEmpty(Optional.empty())
                            .flatMap(owner -> removeWaiting(eventId, owner.orElse(null), token, cutoff)))
                    .then();
        });
    }

    /**
     * 현재 대기 순번과 예상 입장 시각을 응답으로 구성한다.
     */
    public Mono<QueueStatusResponse> waitingStatusResponse(Long eventId, String token) {

        return redis.opsForZSet().rank(keys.waitingKey(eventId), token)
                .map(rank -> Optional.of(rank + 1))
                .defaultIfEmpty(Optional.empty())
                .flatMap(rank -> estimatedEntryAt(eventId, rank.orElse(null))
                        .map(Optional::of)
                        .defaultIfEmpty(Optional.empty())
                        .flatMap(eta -> waitingCount(eventId)
                                .map(count -> new QueueStatusResponse(
                                        eventId, STATUS_WAITING, rank.orElse(null), count, token,
                                        properties.getWaitingTokenTtl().toSeconds(), eta.orElse(null),
                                        eta.map(time -> time.plus(properties.getActiveTokenTtl())).orElse(null)))));
    }

    /**
     * 대기열 처리에서 대기 상태와 연결 정보를 제거한다.
     */
    private Mono<Void> removeWaiting(Long eventId, String userId, String token, long cutoff) {

        boolean hasUser = StringUtils.hasText(userId);
        return redis.execute(QueueRedisScripts.REMOVE_TOKEN, waitingKeys(eventId, userId, token),
                        List.of(token, keys.waitingSessionMember(token), String.valueOf(cutoff), hasUser ? "1" : "0"))
                .then();
    }

    /**
     * 대기열 작업에 사용할 Redis 키 목록을 구성한다.
     */
    private List<String> waitingKeys(Long eventId, String userId, String token) {

        return List.of(keys.waitingKey(eventId), keys.waitingExpiryKey(eventId),
                keys.waitingTokenKey(token), keys.userSessionsKey(userId == null ? "" : userId));
    }

    /**
     * 현재 대기 인원 수를 조회한다.
     */
    public Mono<Long> waitingCount(Long eventId) {

        return redis.opsForZSet().size(keys.waitingKey(eventId))
                .defaultIfEmpty(0L);
    }

    /**
     * 대기 순번과 입장 가능 시각으로 예상 입장 시각을 구한다.
     */
    private Mono<Instant> estimatedEntryAt(Long eventId, Long rank) {

        if (rank == null || rank <= 0) return Mono.empty();
        return Mono.defer(() -> {
            long now = nowMillis();
            return active.activeSlotAvailableTimes(eventId, now)
                    // 순번만큼 반복하는 CPU 계산은 Redis 이벤트 루프 밖에서 실행한다.
                    .flatMap(slots -> Mono.fromSupplier(() -> calculateEntryAt(slots, rank, now))
                            .subscribeOn(Schedulers.parallel()));
        });
    }

    /**
     * 대기 사용자의 예상 입장 시각을 계산한다.
     */
    private Instant calculateEntryAt(PriorityQueue<Long> slots, long rank, long now) {

        if (slots.isEmpty()) return Instant.ofEpochMilli(now);
        long eta = now;
        for (long position = 0; position < rank; position++) {
            eta = Math.max(now, slots.poll());
            slots.add(eta + properties.getActiveTokenTtl().toMillis());
        }
        return Instant.ofEpochMilli(eta);
    }

    /**
     * 스크립트 결과에서 대기열 토큰 값을 추출한다.
     */
    private String tokenValue(Long eventId, String userId) {

        return eventId + ":" + userId;
    }

    /**
     * 대기열 처리에서 현재 시각을 밀리초 단위로 반환한다.
     */
    private long nowMillis() {

        return Instant.now().toEpochMilli();
    }
}
