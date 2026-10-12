package dev.bum.queue_service.service.reactive;

import dev.bum.queue_service.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ReactiveUserQueueSessionService {
    private final ReactiveStringRedisTemplate redis;
    private final QueueRedisKeys keys;

    public Mono<Void> addWaiting(String userId, String token, long expiresAt) {
        return redis.opsForZSet().add(keys.userSessionsKey(userId), keys.waitingSessionMember(token), expiresAt)
                .then();
    }

    public Mono<Void> removeWaiting(String userId, String token) {
        return removeMember(userId, keys.waitingSessionMember(token));
    }

    public Mono<Void> removeActive(String userId, String token) {
        return removeMember(userId, keys.activeSessionMember(token));
    }

    private Mono<Void> removeMember(String userId, String member) {
        return Mono.defer(() -> StringUtils.hasText(userId)
                ? redis.opsForZSet().remove(keys.userSessionsKey(userId), member)
                        .then() : Mono.empty());
    }

    public Mono<Long> count(String userId) {
        return redis.opsForZSet().size(keys.userSessionsKey(userId))
                .defaultIfEmpty(0L);
    }

    public Mono<QueueUserSession> activeSession(String userId) {
        return redis.opsForZSet().range(keys.userSessionsKey(userId), Range.closed(0L, -1L))
                .filter(member -> member.startsWith("active:"))
                .next()
                .flatMap(this::toUserSession);
    }

    public Mono<QueueUserSession> firstExpiringSession(String userId) {
        return redis.opsForZSet().range(keys.userSessionsKey(userId), Range.closed(0L, 0L))
                .next()
                .flatMap(this::toUserSession);
    }

    public Mono<Long> eventIdForSession(String userId, QueueUserSession session) {
        String tokenKey = session.type() == QueueSessionType.WAITING
                ? keys.waitingTokenKey(session.token()) : keys.activeTokenKey(session.token());

        return redis.opsForValue().get(tokenKey)
                .flatMap(value -> {
                    String[] parts = value.split(":", 2);

                    if (parts.length != 2 || !parts[1].equals(userId)) return Mono.empty();

                    try {
                        return Mono.just(Long.valueOf(parts[0]));
                    } catch (NumberFormatException ignored) {
                        return Mono.empty();
                    }
                });
    }

    public Mono<Void> prune(String userId) {
        return redis.opsForZSet().range(keys.userSessionsKey(userId), Range.closed(0L, -1L))
                .collectList()
                .flatMap(members -> {
                    if (members.isEmpty()) return Mono.empty();

                    return redis.opsForValue().multiGet(members.stream().map(this::tokenKey).toList())
                            .flatMap(values -> Flux.range(0, members.size())
                                    .filter(index -> index >= values.size() || !StringUtils.hasText(values.get(index)))
                                    .concatMap(index -> redis.opsForZSet().remove(keys.userSessionsKey(userId), members.get(index)))
                                    .then());
                });
    }

    private Mono<QueueUserSession> toUserSession(String member) {
        QueueSessionType type;
        String token;
        if (member.startsWith("waiting:")) {
            type = QueueSessionType.WAITING;
            token = member.substring("waiting:".length());
        } else if (member.startsWith("active:")) {
            type = QueueSessionType.ACTIVE;
            token = member.substring("active:".length());
        } else {
            return Mono.empty();
        }
        return redis.getExpire(tokenKey(member))
                .map(ReactiveQueueRedisSupport::ttlSeconds)
                .defaultIfEmpty(-1L)
                .map(ttl -> new QueueUserSession(type, token, ttl));
    }

    private String tokenKey(String member) {
        if (member.startsWith("waiting:")) return keys.waitingTokenKey(member.substring("waiting:".length()));
        if (member.startsWith("active:")) return keys.activeTokenKey(member.substring("active:".length()));
        return member;
    }
}
