package dev.bum.queue_service.service.reactive;

import dev.bum.common.service.queue.dto.QueueRedisEntryResponse;
import dev.bum.common.service.queue.dto.QueueRedisInspectResponse;
import dev.bum.common.service.queue.enums.QueueRedisInspectMode;
import dev.bum.queue_service.service.QueueRedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReactiveQueueRedisInspectService {
    private final ReactiveStringRedisTemplate redis;
    private final QueueRedisKeys keys;
    private final ReactiveUserQueueSessionService sessions;
    private final ReactiveActiveQueueService activeQueue;

    public Mono<QueueRedisInspectResponse> inspectEventQueue(Long eventId, QueueRedisInspectMode mode, int limit) {
        return Mono.defer(() -> {
            QueueRedisInspectMode inspectMode = mode == null ? QueueRedisInspectMode.WAITING : mode;
            int normalizedLimit = limit <= 0 ? 100 : Math.min(limit, 500);
            boolean active = inspectMode == QueueRedisInspectMode.ACTIVE;
            String key = active ? keys.activeKey(eventId) : keys.waitingKey(eventId);
            return redis.opsForZSet().rangeWithScores(key, Range.closed(0L, (long) normalizedLimit - 1))
                    .index()
                    .concatMap(indexed -> entry(key, indexed.getT2(), active ? null : indexed.getT1() + 1, active))
                    .collectList()
                    .map(entries -> QueueRedisInspectResponse.builder()
                            .mode(active ? QueueRedisInspectMode.ACTIVE : QueueRedisInspectMode.WAITING)
                            .eventId(eventId)
                            .limit(normalizedLimit)
                            .count(entries.size())
                            .entries(entries)
                            .build());
        });
    }

    public Mono<QueueRedisInspectResponse> inspectToken(String token) {
        String key = keys.activeTokenKey(token);
        return tokenValue(key)
                .flatMap(value -> ttl(key)
                        .map(ttl -> {
                            QueueRedisEntryResponse entry = QueueRedisEntryResponse.builder()
                                    .key(key)
                                    .token(token)
                                    .member(token)
                                    .value(value.orElse(null))
                                    .ttlSeconds(ttl)
                                    .build();
                            return QueueRedisInspectResponse.builder()
                                    .mode(QueueRedisInspectMode.TOKEN)
                                    .token(token)
                                    .limit(1)
                                    .count(1)
                                    .entries(List.of(entry))
                                    .build();
                        }));
    }

    public Mono<Boolean> removeToken(Long eventId, String token, QueueRedisInspectMode mode) {
        return Mono.defer(() -> {
            if (!StringUtils.hasText(token) || mode == null || mode == QueueRedisInspectMode.TOKEN) return Mono.just(false);
            boolean active = mode == QueueRedisInspectMode.ACTIVE;
            String tokenKey = active ? keys.activeTokenKey(token) : keys.waitingTokenKey(token);
            if (active) {
                return tokenValue(tokenKey)
                        .flatMap(value -> {
                            String[] parts = value.orElse("").split(":", 2);
                            String userId = parts.length == 2 ? parts[1] : null;
                            // 완료·탈퇴와 동일한 원자적 삭제 경로로 멱등 매핑도 함께 제거한다.
                            return activeQueue.removeActiveToken(eventId, userId, token);
                        });
            }
            return tokenValue(tokenKey)
                    .flatMap(value ->
                            redis.opsForZSet().remove(active ? keys.activeKey(eventId) : keys.waitingKey(eventId), token)
                                    .flatMap(removed -> (active ? Mono.just(0L)
                                            : redis.opsForZSet().remove(keys.waitingExpiryKey(eventId), token))
                                            .flatMap(expiryRemoved -> redis.delete(tokenKey)
                                                    .flatMap(deleted -> removeSession(value.orElse(null), token, active)
                                                            .thenReturn(removed > 0 || expiryRemoved > 0 || deleted > 0)))));
        });
    }

    private Mono<QueueRedisEntryResponse> entry(String key, ZSetOperations.TypedTuple<String> tuple, Long rank, boolean active) {
        String tokenKey = active ? keys.activeTokenKey(tuple.getValue()) : keys.waitingTokenKey(tuple.getValue());
        return tokenValue(tokenKey)
                .flatMap(value -> ttl(tokenKey)
                        .map(ttl -> {
                            var builder = QueueRedisEntryResponse.builder()
                                    .key(key)
                                    .member(tuple.getValue())
                                    .token(tuple.getValue())
                                    .score(tuple.getScore())
                                    .timestampMillis(tuple.getScore() == null ? null : tuple.getScore().longValue())
                                    .value(value.orElse(null))
                                    .ttlSeconds(ttl);
                            if (!active) builder.rank(rank);
                            return builder.build();
                        }));
    }

    private Mono<Optional<String>> tokenValue(String key) {
        // GET 결과가 없어도 관리자 조회 항목과 TTL 정보는 응답에 남긴다.
        return redis.opsForValue().get(key)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    private Mono<Long> ttl(String key) {
        return redis.getExpire(key)
                .defaultIfEmpty(Duration.ofSeconds(-2))
                .map(ReactiveQueueRedisSupport::ttlSeconds);
    }

    private Mono<Void> removeSession(String value, String token, boolean active) {
        if (!StringUtils.hasText(value)) return Mono.empty();
        String[] parts = value.split(":", 2);
        if (parts.length != 2 || !StringUtils.hasText(parts[1])) return Mono.empty();
        return active ? sessions.removeActive(parts[1], token) : sessions.removeWaiting(parts[1], token);
    }
}
