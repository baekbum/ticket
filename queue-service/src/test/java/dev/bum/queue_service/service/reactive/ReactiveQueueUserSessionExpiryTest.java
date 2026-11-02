package dev.bum.queue_service.service.reactive;

import dev.bum.common.service.queue.dto.QueueEnterResponse;
import dev.bum.common.service.queue.dto.QueueStatusResponse;
import dev.bum.queue_service.QueuePolicyFixture;
import dev.bum.queue_service.QueueServiceApplication;
import dev.bum.queue_service.exception.QueueTokenInvalidException;
import dev.bum.queue_service.service.QueueRedisKeys;
import dev.bum.queue_service.service.QueueRedisScripts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@EnabledIfEnvironmentVariable(named = "QUEUE_TEST_REDIS_PORT", matches = "\\d+")
@SpringBootTest(classes = QueueServiceApplication.class, properties = {
        "spring.profiles.active=baseline",
        "spring.data.redis.host=127.0.0.1", "spring.data.redis.port=${QUEUE_TEST_REDIS_PORT}",
        "spring.data.redis.database=3",
        "token-state.redis.host=127.0.0.1", "token-state.redis.port=${QUEUE_TEST_REDIS_PORT}",
        "token-state.redis.database=0",
        "token.secret=queue-baseline-only-secret-at-least-32-bytes-long",
        "app.queue.admission-size=2", "app.queue.max-sessions-per-user=2",
        "app.queue.cleanup-mode=disabled", "management.tracing.enabled=false"
})
class ReactiveQueueUserSessionExpiryTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final RedisScript<Long> EXPIRY = RedisScript.of("return redis.call('PEXPIRETIME', KEYS[1])", Long.class);

    @Autowired
    private ReactiveQueueService queue;

    @Autowired
    private ReactiveWaitingQueueService waiting;

    @Autowired
    private ReactiveUserQueueSessionService sessions;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ReactiveStringRedisTemplate reactiveRedis;

    @Autowired
    private QueueRedisKeys keys;

    private final Set<String> fixtureKeys = new HashSet<>();
    private long event;
    private long otherEvent;
    private String user;

    @BeforeEach
    void fixture() {
        event = UUID.randomUUID().getMostSignificantBits() & (Long.MAX_VALUE - 1);
        otherEvent = event + 1;
        user = "session-expiry-" + UUID.randomUUID();
        for (long eventId : List.of(event, otherEvent)) {
            QueuePolicyFixture.seed(redis, eventId, 2);
            fixtureKeys.addAll(List.of(keys.policyKey(eventId), keys.waitingKey(eventId),
                    keys.waitingExpiryKey(eventId), keys.activeKey(eventId)));
        }
        fixtureKeys.add(keys.userSessionsKey(user));
    }

    @AfterEach
    void cleanup() {
        redis.delete(fixtureKeys);
        fixtureKeys.clear();
    }

    @Test
    void registration_uses_the_same_expiry_for_the_token_and_both_indexes() {
        String token = enter(event);
        long expiresAt = expiresAt(keys.waitingTokenKey(token));

        assertThat(expiresAt(keys.userSessionsKey(user))).isEqualTo(expiresAt);
        assertThat(redis.opsForZSet().score(keys.userSessionsKey(user), keys.waitingSessionMember(token)))
                .isEqualTo((double) expiresAt);
        assertThat(redis.opsForZSet().score(keys.waitingExpiryKey(event), token)).isEqualTo((double) expiresAt);
    }

    @Test
    void the_index_disappears_after_natural_expiry_without_a_followup_request_or_scheduler() {
        String token = register(event, 1000);
        assertThat(redis.hasKey(keys.userSessionsKey(user))).isTrue();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(redis.hasKey(keys.waitingTokenKey(token))).isFalse();
            assertThat(redis.hasKey(keys.userSessionsKey(user))).isFalse();
        });
    }

    @Test
    void refreshing_a_waiting_token_also_extends_the_index() {
        String token = register(event, 5000);
        long originalExpiry = expiresAt(keys.userSessionsKey(user));

        waiting.refreshWaitingToken(event, user, token).block(TIMEOUT);

        long renewedExpiry = expiresAt(keys.waitingTokenKey(token));
        assertThat(renewedExpiry).isGreaterThan(originalExpiry);
        assertThat(expiresAt(keys.userSessionsKey(user))).isEqualTo(renewedExpiry);
    }

    @Test
    void waiting_status_refreshes_the_index_when_capacity_is_full() {
        String token = register(event, 5000);
        long originalExpiry = expiresAt(keys.userSessionsKey(user));
        redis.opsForZSet().add(keys.activeKey(event), "occupied-one", originalExpiry + 120_000);
        redis.opsForZSet().add(keys.activeKey(event), "occupied-two", originalExpiry + 120_000);

        QueueStatusResponse response = queue.status(event, user, token).block(TIMEOUT);

        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(expiresAt(keys.userSessionsKey(user))).isGreaterThan(originalExpiry)
                .isEqualTo(expiresAt(keys.waitingTokenKey(token)));
    }

    @Test
    void promotion_moves_the_expiry_to_the_active_session_and_replays_do_not_extend_it() {
        String token = enter(event);
        String activeToken = admit(event, token);
        long activeExpiry = expiresAt(keys.activeTokenKey(activeToken));
        assertThat(expiresAt(keys.userSessionsKey(user))).isEqualTo(activeExpiry);
        assertThat(redis.hasKey(keys.waitingTokenKey(token))).isFalse();

        QueueStatusResponse replay = queue.status(event, user, token).block(TIMEOUT);

        assertThat(replay.token()).isEqualTo(activeToken);
        assertThat(expiresAt(keys.activeTokenKey(activeToken))).isEqualTo(activeExpiry);
        assertThat(expiresAt(keys.userSessionsKey(user))).isEqualTo(activeExpiry);
    }

    @Test
    void a_shorter_new_session_or_refresh_does_not_shorten_another_events_active_session() {
        String activeToken = admit(otherEvent, enter(otherEvent));
        long activeExpiry = expiresAt(keys.activeTokenKey(activeToken));
        String shortToken = register(event, 5000);
        assertThat(expiresAt(keys.userSessionsKey(user))).isEqualTo(activeExpiry);

        waiting.refreshWaitingToken(event, user, shortToken).block(TIMEOUT);

        assertThat(expiresAt(keys.userSessionsKey(user))).isEqualTo(activeExpiry);
    }

    @Test
    void completing_the_last_expiring_session_recomputes_the_expiry_and_leaving_the_last_removes_the_key() {
        String waitingToken = enter(event);
        String activeToken = admit(otherEvent, enter(otherEvent));

        assertThat(queue.complete(otherEvent, user, activeToken).block(TIMEOUT)).isTrue();
        assertThat(expiresAt(keys.userSessionsKey(user))).isEqualTo(expiresAt(keys.waitingTokenKey(waitingToken)));

        assertThat(queue.leave(event, user, waitingToken, null).block(TIMEOUT)).isTrue();
        assertThat(redis.hasKey(keys.userSessionsKey(user))).isFalse();
    }

    @Test
    void direct_member_removal_and_pruning_update_expiry_and_remove_an_empty_index() {
        String first = register(event, 5000);
        String last = register(otherEvent, 10_000);
        redis.delete(keys.waitingTokenKey(last));

        sessions.removeWaiting(user, last).block(TIMEOUT);
        assertThat(expiresAt(keys.userSessionsKey(user))).isEqualTo(expiresAt(keys.waitingTokenKey(first)));

        redis.delete(keys.waitingTokenKey(first));
        sessions.prune(user).block(TIMEOUT);
        assertThat(redis.hasKey(keys.userSessionsKey(user))).isFalse();
    }

    @Test
    void an_invalid_refresh_does_not_extend_the_user_index() {
        String token = register(event, 5000);
        long originalExpiry = expiresAt(keys.userSessionsKey(user));
        redis.delete(keys.waitingTokenKey(token));

        StepVerifier.create(waiting.refreshWaitingToken(event, user, token))
                .expectError(QueueTokenInvalidException.class).verify(TIMEOUT);

        assertThat(expiresAt(keys.userSessionsKey(user))).isEqualTo(originalExpiry);
    }

    @Test
    void concurrent_registrations_preserve_the_latest_session_expiry() {
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();
        trackWaiting(first);
        trackWaiting(second);

        List<Long> results = Flux.merge(registerCommand(event, first, 60_000),
                registerCommand(otherEvent, second, 120_000)).collectList().block(TIMEOUT);

        assertThat(results).containsExactly(1L, 1L);
        assertThat(expiresAt(keys.userSessionsKey(user))).isEqualTo(Math.max(
                expiresAt(keys.waitingTokenKey(first)), expiresAt(keys.waitingTokenKey(second))));
        assertThat(redis.opsForZSet().zCard(keys.userSessionsKey(user))).isEqualTo(2);
    }

    @Test
    void scheduled_cleanup_removes_a_persistent_legacy_index_without_a_user_visit() {
        String missingToken = "missing-" + UUID.randomUUID();
        redis.opsForZSet().add(keys.userSessionsKey(user), keys.waitingSessionMember(missingToken), 0);
        assertThat(expiresAt(keys.userSessionsKey(user))).isEqualTo(-1L);

        queue.cleanupExpiredTokens().block(TIMEOUT);

        assertThat(redis.hasKey(keys.userSessionsKey(user))).isFalse();
    }

    @Test
    void scheduled_cleanup_repairs_a_live_legacy_index_using_the_actual_token_expiry() {
        String token = enter(event);
        redis.persist(keys.userSessionsKey(user));
        redis.opsForZSet().add(keys.userSessionsKey(user), keys.waitingSessionMember(token), 0);
        assertThat(expiresAt(keys.userSessionsKey(user))).isEqualTo(-1L);

        queue.cleanupExpiredTokens().block(TIMEOUT);

        long tokenExpiry = expiresAt(keys.waitingTokenKey(token));
        assertThat(tokenExpiry).isPositive();
        assertThat(expiresAt(keys.userSessionsKey(user))).isEqualTo(tokenExpiry);
        assertThat(redis.opsForZSet().score(keys.userSessionsKey(user), keys.waitingSessionMember(token)))
                .isEqualTo((double) tokenExpiry);
    }

    private String enter(long eventId) {
        QueueEnterResponse response = queue.enter(eventId, user, false).block(TIMEOUT);
        assertThat(response.status()).isEqualTo("WAITING");
        trackWaiting(response.token());
        return response.token();
    }

    private String admit(long eventId, String waitingToken) {
        QueueStatusResponse response = queue.status(eventId, user, waitingToken).block(TIMEOUT);
        assertThat(response.status()).isEqualTo("READY");
        fixtureKeys.add(keys.activeTokenKey(response.token()));
        fixtureKeys.add(keys.activeWaitingMappingKey(response.token()));
        return response.token();
    }

    private String register(long eventId, long ttlMillis) {
        String token = UUID.randomUUID().toString();
        trackWaiting(token);
        assertThat(registerCommand(eventId, token, ttlMillis).block(TIMEOUT)).isEqualTo(1L);
        return token;
    }

    private Mono<Long> registerCommand(long eventId, String token, long ttlMillis) {
        return reactiveRedis.execute(QueueRedisScripts.REGISTER_WAITING,
                List.of(keys.waitingKey(eventId), keys.waitingExpiryKey(eventId), keys.waitingTokenKey(token),
                        keys.userSessionsKey(user), keys.policyKey(eventId)),
                List.of(token, eventId + ":" + user, String.valueOf(System.currentTimeMillis()),
                        String.valueOf(ttlMillis), "2", keys.waitingSessionMember(token))).next();
    }

    private void trackWaiting(String token) {
        fixtureKeys.add(keys.waitingTokenKey(token));
        fixtureKeys.add(keys.waitingActiveMappingKey(token));
    }

    private long expiresAt(String key) {
        return redis.execute(EXPIRY, List.of(key));
    }
}
