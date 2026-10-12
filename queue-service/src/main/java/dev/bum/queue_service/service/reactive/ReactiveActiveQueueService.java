package dev.bum.queue_service.service.reactive;

import dev.bum.common.service.queue.dto.QueueStatusResponse;
import dev.bum.queue_service.config.QueueProperties;
import dev.bum.queue_service.service.QueueRedisKeys;
import dev.bum.queue_service.service.QueueRedisScripts;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReactiveActiveQueueService {
    public static final String STATUS_READY = "READY";
    private final ReactiveStringRedisTemplate redis;
    private final QueueProperties properties;
    private final QueueRedisKeys keys;
    private final ReactiveUserQueueSessionService sessions;

    public Mono<QueueStatusResponse> readyStatusResponse(Long eventId, String activeToken) {
        return redis.opsForZSet().score(keys.activeKey(eventId), activeToken)
                .map(Optional::of).defaultIfEmpty(Optional.empty())
                .flatMap(score -> redis.opsForZSet().size(keys.waitingKey(eventId)).defaultIfEmpty(0L)
                        .map(waitingCount -> {
                            Long expiresAt = score.map(Double::longValue).orElse(null);
                            return new QueueStatusResponse(eventId, STATUS_READY, 0L, waitingCount, activeToken,
                                    expiresAt == null ? null : Math.max(0L, (expiresAt - nowMillis()) / 1000),
                                    Instant.now(), expiresAt == null ? null : Instant.ofEpochMilli(expiresAt));
                        }));
    }

    /** 승인되지 않으면 빈 Mono로 완료한다. 토큰과 시각은 구독할 때 생성한다. */
    public Mono<String> admit(Long eventId, String userId, String waitingToken) {
        return Mono.defer(() -> {
            String activeToken = UUID.randomUUID().toString();
            long expiresAt = nowMillis() + properties.getActiveTokenTtl().toMillis();
            return redis.execute(QueueRedisScripts.ADMIT_IF_SLOT_AVAILABLE,
                            List.of(keys.waitingKey(eventId), keys.activeKey(eventId), keys.activeTokenKey(activeToken),
                                    keys.userSessionsKey(userId), keys.waitingTokenKey(waitingToken), keys.waitingExpiryKey(eventId)),
                            List.of(waitingToken, activeToken, tokenValue(eventId, userId), String.valueOf(expiresAt),
                                    String.valueOf(properties.getActiveTokenTtl().toMillis()),
                                    String.valueOf(properties.getAdmissionSize()), keys.activeSessionMember(activeToken),
                                    keys.waitingSessionMember(waitingToken)))
                    .next().filter(result -> result == 1L).map(result -> activeToken);
        });
    }

    public Mono<Boolean> isActiveTokenValid(Long eventId, String userId, String token) {
        return Mono.defer(() -> {
            if (!StringUtils.hasText(token)) return Mono.just(false);
            return redis.opsForValue().get(keys.activeTokenKey(token))
                    .filter(value -> tokenValue(eventId, userId).equals(value))
                    .flatMap(value -> redis.opsForZSet().score(keys.activeKey(eventId), token).hasElement())
                    .defaultIfEmpty(false);
        });
    }

    public Mono<Map<String, String>> activeTokenByUserId(Long eventId, List<String> userIds) {
        return Flux.fromIterable(userIds).concatMap(userId -> sessions.activeSession(userId)
                        .flatMap(session -> isActiveTokenValid(eventId, userId, session.token())
                                .filter(Boolean::booleanValue).map(valid -> Map.entry(userId, session.token()))))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    public Mono<Boolean> removeActiveToken(Long eventId, String userId, String token) {
        return removeActive(eventId, userId, token, -1L).thenReturn(true);
    }

    private Mono<Void> removeActive(Long eventId, String userId, String token, long cutoff) {
        return redis.execute(QueueRedisScripts.REMOVE_TOKEN,
                List.of(keys.activeKey(eventId), keys.activeKey(eventId), keys.activeTokenKey(token),
                        keys.userSessionsKey(userId == null ? "" : userId)),
                List.of(token, keys.activeSessionMember(token), String.valueOf(cutoff),
                        StringUtils.hasText(userId) ? "1" : "0")).then();
    }

    public Mono<Void> pruneExpiredActiveTokens(Long eventId) {
        return Mono.defer(() -> {
            long cutoff = nowMillis();
            return redis.opsForZSet().rangeByScore(keys.activeKey(eventId), Range.closed(0.0, (double) cutoff))
                    .concatMap(token -> redis.opsForValue().get(keys.activeTokenKey(token))
                            .map(value -> value.split(":", 2)).filter(parts -> parts.length == 2)
                            .map(parts -> Optional.of(parts[1])).defaultIfEmpty(Optional.empty())
                            .flatMap(owner -> removeActive(eventId, owner.orElse(null), token, cutoff))).then();
        });
    }
    public Mono<PriorityQueue<Long>> activeSlotAvailableTimes(Long eventId, long now) {
        return redis.opsForZSet().rangeWithScores(keys.activeKey(eventId), Range.closed(0L, -1L))
                .filter(tuple -> tuple.getScore() != null).map(tuple -> Math.max(now, tuple.getScore().longValue()))
                .sort().collectList().map(expirations -> {
                    PriorityQueue<Long> slots = new PriorityQueue<>();
                    int admissionSize = properties.getAdmissionSize();
                    expirations.stream().skip(Math.max(0, expirations.size() - admissionSize)).forEach(slots::add);
                    for (int slot = expirations.size(); slot < admissionSize; slot++) slots.add(now);
                    return slots;
                });
    }

    private String tokenValue(Long eventId, String userId) { return eventId + ":" + userId; }
    private long nowMillis() { return Instant.now().toEpochMilli(); }
}
