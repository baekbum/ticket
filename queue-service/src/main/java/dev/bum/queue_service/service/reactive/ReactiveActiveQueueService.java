package dev.bum.queue_service.service.reactive;

import dev.bum.common.service.queue.dto.QueueStatusResponse;
import dev.bum.queue_service.config.QueueProperties;
import dev.bum.queue_service.exception.QueueTokenInvalidException;
import org.springframework.dao.DataAccessResourceFailureException;
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
                .switchIfEmpty(Mono.error(invalidToken()))
                .flatMap(score -> redis.opsForZSet().size(keys.waitingKey(eventId))
                        .defaultIfEmpty(0L)
                        .map(waitingCount -> {
                            long expiresAt = score.longValue();
                            return new QueueStatusResponse(eventId, STATUS_READY, 0L, waitingCount, activeToken,
                                    Math.max(0L, (expiresAt - nowMillis()) / 1000),
                                    Instant.now(), Instant.ofEpochMilli(expiresAt));
                        }));
    }

    /** 승인 또는 재요청이면 같은 active 토큰을 반환하고, 대기 상태면 빈 Mono로 완료한다. */
    public Mono<String> admit(Long eventId, String userId, String waitingToken) {
        return admit(eventId, userId, waitingToken, 0);
    }

    private Mono<String> admit(Long eventId, String userId, String waitingToken, int attempt) {
        return Mono.defer(() -> {
            if (!StringUtils.hasText(waitingToken)) return Mono.error(invalidToken());

            String activeToken = UUID.randomUUID().toString();

            return redis.opsForValue().get(keys.waitingActiveMappingKey(waitingToken))
                    .defaultIfEmpty("")
                    .flatMap(existing -> redis.execute(QueueRedisScripts.STATUS_WITH_IDEMPOTENCY,
                            List.of(keys.waitingKey(eventId), keys.activeKey(eventId), keys.activeTokenKey(activeToken),
                                    keys.userSessionsKey(userId), keys.waitingTokenKey(waitingToken), keys.waitingExpiryKey(eventId),
                                    keys.waitingActiveMappingKey(waitingToken), keys.activeWaitingMappingKey(activeToken),
                                    keys.activeTokenKey(existing.isEmpty() ? activeToken : existing),
                                    keys.activeWaitingMappingKey(existing.isEmpty() ? activeToken : existing)),
                            List.of(waitingToken, activeToken, tokenValue(eventId, userId),
                                    String.valueOf(properties.getActiveTokenTtl().toMillis()),
                                    String.valueOf(properties.getAdmissionSize()),
                                    String.valueOf(properties.getWaitingTokenTtl().toMillis()),
                                    keys.activeSessionMember(activeToken), keys.waitingSessionMember(waitingToken), existing))
                            .next())
                    .switchIfEmpty(Mono.error(new DataAccessResourceFailureException("입장 상태 조회 결과가 없습니다.")))
                    .flatMap(result -> {
                        if (result.startsWith("READY:") && StringUtils.hasText(result.substring(6))) {
                            return Mono.just(result.substring(6));
                        }
                        if ("WAITING".equals(result)) return Mono.empty();
                        if ("INVALID".equals(result)) return Mono.error(invalidToken());
                        // 첫 조회와 Lua 실행 사이에 승격한 요청이 있으면 정확한 키로 다시 검증한다.
                        if ("RETRY".equals(result) && attempt < 2) {
                            return admit(eventId, userId, waitingToken, attempt + 1);
                        }
                        return Mono.error(new DataAccessResourceFailureException("입장 토큰 매핑 상태를 확인할 수 없습니다."));
                    });
        });
    }

    private QueueTokenInvalidException invalidToken() {
        return new QueueTokenInvalidException("대기 토큰이 만료되었거나 이미 종료된 세션입니다.");
    }

    public Mono<Boolean> isActiveTokenValid(Long eventId, String userId, String token) {
        return Mono.defer(() -> {
            if (!StringUtils.hasText(token)) return Mono.just(false);
            return redis.opsForValue().get(keys.activeTokenKey(token))
                    .filter(value -> tokenValue(eventId, userId).equals(value))
                    .flatMap(value -> redis.opsForZSet().score(keys.activeKey(eventId), token)
                            .hasElement())
                    .defaultIfEmpty(false);
        });
    }

    public Mono<Map<String, String>> activeTokenByUserId(Long eventId, List<String> userIds) {
        return Flux.fromIterable(userIds)
                .concatMap(userId -> sessions.activeSession(userId)
                        .flatMap(session -> isActiveTokenValid(eventId, userId, session.token())
                                .filter(Boolean::booleanValue)
                                .map(valid -> Map.entry(userId, session.token()))))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    public Mono<Boolean> removeActiveToken(Long eventId, String userId, String token) {
        return removeActive(eventId, userId, token, -1L);
    }

    private Mono<Boolean> removeActive(Long eventId, String userId, String token, long cutoff) {
        return redis.opsForValue().get(keys.activeWaitingMappingKey(token))
                .defaultIfEmpty("")
                .flatMap(waitingToken -> redis.execute(QueueRedisScripts.REMOVE_TOKEN,
                        List.of(keys.activeKey(eventId), keys.activeKey(eventId), keys.activeTokenKey(token),
                                keys.userSessionsKey(userId == null ? "" : userId),
                                keys.activeWaitingMappingKey(token), keys.waitingActiveMappingKey(waitingToken)),
                        List.of(token, keys.activeSessionMember(token), String.valueOf(cutoff),
                                StringUtils.hasText(userId) ? "1" : "0", waitingToken))
                        .next())
                .map(removed -> removed > 0)
                .defaultIfEmpty(false);
    }

    Flux<String> mappingKeysForActive(String token) {
        return Flux.concat(Flux.just(keys.activeWaitingMappingKey(token)),
                redis.opsForValue().get(keys.activeWaitingMappingKey(token))
                        .map(keys::waitingActiveMappingKey));
    }

    public Mono<Void> pruneExpiredActiveTokens(Long eventId) {
        return Mono.defer(() -> {
            long cutoff = nowMillis();
            return redis.opsForZSet().rangeByScore(keys.activeKey(eventId), Range.closed(0.0, (double) cutoff))
                    .concatMap(token -> redis.opsForValue().get(keys.activeTokenKey(token))
                            .map(value -> value.split(":", 2))
                            .filter(parts -> parts.length == 2)
                            .map(parts -> Optional.of(parts[1]))
                            .defaultIfEmpty(Optional.empty())
                            .flatMap(owner -> removeActive(eventId, owner.orElse(null), token, cutoff)))
                    .then();
        });
    }

    public Mono<PriorityQueue<Long>> activeSlotAvailableTimes(Long eventId, long now) {
        return redis.opsForZSet().rangeWithScores(keys.activeKey(eventId), Range.closed(0L, -1L))
                .filter(tuple -> tuple.getScore() != null)
                .map(tuple -> Math.max(now, tuple.getScore().longValue()))
                .sort()
                .collectList()
                .map(expirations -> {
                    PriorityQueue<Long> slots = new PriorityQueue<>();
                    int admissionSize = properties.getAdmissionSize();
                    expirations.stream().skip(Math.max(0, expirations.size() - admissionSize)).forEach(slots::add);
                    for (int slot = expirations.size(); slot < admissionSize; slot++) slots.add(now);
                    return slots;
                });
    }

    private String tokenValue(Long eventId, String userId) {
        return eventId + ":" + userId;
    }

    private long nowMillis() {
        return Instant.now().toEpochMilli();
    }
}
