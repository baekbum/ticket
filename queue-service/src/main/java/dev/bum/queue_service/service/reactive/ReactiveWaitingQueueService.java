package dev.bum.queue_service.service.reactive;

import dev.bum.common.service.queue.dto.QueueStatusResponse;
import dev.bum.queue_service.config.QueueProperties;
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
    public static final String STATUS_WAITING = "WAITING";
    private final ReactiveStringRedisTemplate redis;
    private final QueueProperties properties;
    private final QueueRedisKeys keys;
    private final ReactiveActiveQueueService active;
    private final ReactiveUserQueueSessionService sessions;

    /** 세션 한도에 도달하면 빈 Mono로 완료한다. */
    public Mono<String> createWaiting(Long eventId, String userId) {
        return Mono.defer(() -> {
            String token = UUID.randomUUID().toString();
            long now = nowMillis();
            long ttl = properties.getWaitingTokenTtl().toMillis();
            return redis.execute(QueueRedisScripts.REGISTER_WAITING, waitingKeys(eventId, userId, token),
                            List.of(token, tokenValue(eventId, userId), String.valueOf(now), String.valueOf(now + ttl),
                                    String.valueOf(ttl), String.valueOf(properties.getMaxSessionsPerUser()),
                                    keys.waitingSessionMember(token)))
                    .next().filter(result -> result == 1L).map(result -> token);
        });
    }
    public Mono<QueueStatusResponse> waitingStatusResponse(Long eventId, String userId, String token) {
        return redis.opsForZSet().rank(keys.waitingKey(eventId), token).map(rank -> Optional.of(rank + 1))
                .defaultIfEmpty(Optional.empty()).flatMap(rank -> estimatedEntryAt(eventId, rank.orElse(null))
                        .map(Optional::of).defaultIfEmpty(Optional.empty())
                        .flatMap(eta -> waitingCount(eventId).map(count -> new QueueStatusResponse(
                                eventId, STATUS_WAITING, rank.orElse(null), count, token,
                                properties.getWaitingTokenTtl().toSeconds(), eta.orElse(null),
                                eta.map(time -> time.plus(properties.getActiveTokenTtl())).orElse(null)))));
    }

    public Mono<Boolean> isWaitingTokenValid(Long eventId, String userId, String token) {
        return Mono.defer(() -> {
            if (!StringUtils.hasText(token)) return Mono.just(false);
            return redis.opsForValue().get(keys.waitingTokenKey(token))
                    .filter(value -> tokenValue(eventId, userId).equals(value))
                    .flatMap(value -> redis.opsForZSet().score(keys.waitingKey(eventId), token).hasElement())
                    .defaultIfEmpty(false);
        });
    }

    public Mono<Void> removeWaitingToken(Long eventId, String userId, String token) {
        return removeWaiting(eventId, userId, token, -1L);
    }

    private Mono<Void> removeWaiting(Long eventId, String userId, String token, long cutoff) {
        boolean hasUser = StringUtils.hasText(userId);
        return redis.execute(QueueRedisScripts.REMOVE_TOKEN, waitingKeys(eventId, userId, token),
                List.of(token, keys.waitingSessionMember(token), String.valueOf(cutoff), hasUser ? "1" : "0")).then();
    }

    private List<String> waitingKeys(Long eventId, String userId, String token) {
        return List.of(keys.waitingKey(eventId), keys.waitingExpiryKey(eventId),
                keys.waitingTokenKey(token), keys.userSessionsKey(userId == null ? "" : userId));
    }
    public Mono<Long> waitingCount(Long eventId) {
        return redis.opsForZSet().size(keys.waitingKey(eventId)).defaultIfEmpty(0L);
    }

    public Mono<Void> pruneExpiredWaitingTokens(Long eventId) {
        return pruneExpiredWaitingTokens(eventId, null);
    }

    public Mono<Void> pruneExpiredWaitingTokens(Long eventId, String userId) {
        return Mono.defer(() -> {
            long cutoff = nowMillis();
            return redis.opsForZSet().rangeByScore(keys.waitingExpiryKey(eventId), Range.closed(0.0, (double) cutoff))
                    .concatMap(token -> redis.opsForValue().get(keys.waitingTokenKey(token))
                            .map(value -> value.split(":", 2)).filter(parts -> parts.length == 2)
                            .map(parts -> Optional.of(parts[1])).defaultIfEmpty(Optional.empty())
                            .flatMap(owner -> removeWaiting(eventId, owner.orElse(null), token, cutoff))).then();
        });
    }

    public Mono<Void> refreshWaitingToken(Long eventId, String userId, String token) {
        return Mono.defer(() -> {
            long ttl = properties.getWaitingTokenTtl().toMillis();
            return redis.execute(QueueRedisScripts.REFRESH_WAITING, waitingKeys(eventId, userId, token),
                            List.of(token, tokenValue(eventId, userId), String.valueOf(ttl),
                                    String.valueOf(nowMillis() + ttl), keys.waitingSessionMember(token)))
                    .next().filter(result -> result == 1L)
                    .switchIfEmpty(Mono.error(new QueueTokenInvalidException("대기 토큰이 만료되었거나 이미 처리되었습니다.")))
                    .then();
        });
    }
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

    private Instant calculateEntryAt(PriorityQueue<Long> slots, long rank, long now) {
        if (slots.isEmpty()) return Instant.ofEpochMilli(now);
        long eta = now;
        for (long position = 0; position < rank; position++) {
            eta = Math.max(now, slots.poll());
            slots.add(eta + properties.getActiveTokenTtl().toMillis());
        }
        return Instant.ofEpochMilli(eta);
    }

    private String tokenValue(Long eventId, String userId) { return eventId + ":" + userId; }

    private long nowMillis() { return Instant.now().toEpochMilli(); }
}
