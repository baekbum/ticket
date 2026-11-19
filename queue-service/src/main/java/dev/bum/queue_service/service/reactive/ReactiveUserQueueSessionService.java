package dev.bum.queue_service.service.reactive;

import dev.bum.queue_service.service.QueueRedisKeys;
import dev.bum.queue_service.service.QueueRedisScripts;
import dev.bum.queue_service.service.QueueSessionType;
import dev.bum.queue_service.service.QueueUserSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReactiveUserQueueSessionService {

    // 대기열 상태와 토큰을 저장하고 조회하는 Redis 클라이언트.
    private final ReactiveStringRedisTemplate redis;
    // 대기열 작업에 사용할 Redis 키 생성 도구.
    private final QueueRedisKeys keys;

    /**
     * 조건에 맞는 항목 수를 조회한다.
     */
    public Mono<Long> count(String userId) {

        return redis.opsForZSet().size(keys.userSessionsKey(userId))
                .defaultIfEmpty(0L);
    }

    /**
     * 사용자 처리에서 대기 상태와 연결 정보를 제거한다.
     */
    public Mono<Void> removeWaiting(String userId, String token) {

        return removeMember(userId, keys.waitingSessionMember(token));
    }

    /**
     * 사용자 처리에서 입장 상태와 연결 정보를 제거한다.
     */
    public Mono<Void> removeActive(String userId, String token) {

        return removeMember(userId, keys.activeSessionMember(token));
    }

    /**
     * 만료되거나 유효하지 않은 항목을 정리한다.
     */
    public Mono<Void> prune(String userId) {

        return redis.opsForZSet().range(keys.userSessionsKey(userId), Range.closed(0L, -1L))
                .collectList()
                .flatMap(members -> {
                    if (members.isEmpty()) return Mono.empty();

                    List<String> scriptKeys = new ArrayList<>();
                    scriptKeys.add(keys.userSessionsKey(userId));
                    members.stream().map(this::tokenKey).forEach(scriptKeys::add);
                    return redis.execute(QueueRedisScripts.PRUNE_USER_SESSIONS, scriptKeys, members).then();
                });
    }

    /** 이전 버전에서 TTL 없이 남은 인덱스를 요청 재방문 없이 정리·보정한다. */
    public Mono<Void> prunePersistentIndexes(long scanCount) {

        String prefix = keys.userSessionsKey("");
        return redis.scan(ScanOptions.scanOptions().match(prefix + "*").count(scanCount).build())
                .concatMap(key -> redis.getExpire(key)
                        // ReactiveRedisTemplate은 Redis의 만료 없음(-1)을 Duration.ZERO로 변환한다.
                        .filter(Duration::isZero)
                        .flatMap(ttl -> prune(key.substring(prefix.length()))))
                .then();
    }

    /**
     * 사용자 세션 목록에서 지정한 항목을 제거한다.
     */
    private Mono<Void> removeMember(String userId, String member) {

        return Mono.defer(() -> StringUtils.hasText(userId)
                ? redis.execute(QueueRedisScripts.REMOVE_USER_SESSION, List.of(keys.userSessionsKey(userId)), List.of(member))
                        .then() : Mono.empty());
    }

    /**
     * 입장 토큰을 사용자 세션 정보로 구성한다.
     */
    public Mono<QueueUserSession> activeSession(String userId) {

        return redis.opsForZSet().range(keys.userSessionsKey(userId), Range.closed(0L, -1L))
                .filter(member -> member.startsWith("active:"))
                .next()
                .flatMap(this::toUserSession);
    }

    /**
     * 가장 먼저 만료되는 사용자 세션을 조회한다.
     */
    public Mono<QueueUserSession> firstExpiringSession(String userId) {

        return redis.opsForZSet().range(keys.userSessionsKey(userId), Range.closed(0L, 0L))
                .next()
                .flatMap(this::toUserSession);
    }

    /**
     * 사용자 세션에 연결된 공연 식별자를 조회한다.
     */
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

    /**
     * Redis 항목을 사용자 대기열 세션으로 변환한다.
     */
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

    /**
     * 토큰 유형에 맞는 Redis 키를 구성한다.
     */
    private String tokenKey(String member) {

        if (member.startsWith("waiting:")) return keys.waitingTokenKey(member.substring("waiting:".length()));

        if (member.startsWith("active:")) return keys.activeTokenKey(member.substring("active:".length()));
        return member;
    }
}
