package dev.bum.queue_service.service.reactive;

import dev.bum.common.service.queue.enums.QueueRedisInspectMode;
import dev.bum.queue_service.QueueServiceApplication;
import dev.bum.queue_service.config.QueueProperties;
import dev.bum.queue_service.exception.QueueTokenInvalidException;
import dev.bum.queue_service.service.QueueRedisKeys;
import dev.bum.queue_service.service.QueueRedisScripts;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

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
class ReactiveQueueIdempotencyTest {
    @Autowired ReactiveQueueService queue;
    @Autowired StringRedisTemplate redis;
    @Autowired ReactiveStringRedisTemplate reactiveRedis;
    @Autowired ReactiveActiveQueueService active;
    @Autowired ReactiveUserQueueSessionService sessions;
    @Autowired ReactiveQueueRedisInspectService inspect;
    @Autowired ReactiveQueueTestManagementService management;
    private final List<String> waitingTokens = new ArrayList<>();
    private long event;
    private String user;

    @BeforeEach
    void fixture() {
        event = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        user = "idempotency-" + UUID.randomUUID();
    }

    private <T> T value(Mono<T> publisher) {
        AtomicReference<T> result = new AtomicReference<>();
        StepVerifier.create(publisher).assertNext(result::set).expectComplete().verify(Duration.ofSeconds(10));
        return result.get();
    }

    private String enter() {
        String token = value(queue.enter(event, user, false)).token();
        waitingTokens.add(token);
        return token;
    }

    private String forward(String waiting) { return "queue:waiting-active:" + waiting; }
    private String reverse(String activeToken) { return "queue:active-waiting:" + activeToken; }

    private Long expiresAt(String key) {
        return redis.execute(RedisScript.of(
                "return redis.call('PEXPIRETIME', KEYS[1])", Long.class), List.of(key));    }

    private void invalid(Mono<?> request) {
        StepVerifier.create(request).expectError(QueueTokenInvalidException.class).verify(Duration.ofSeconds(10));
    }

    private void mappingRemoved(String waiting, String activeToken) {
        assertThat(redis.hasKey(forward(waiting))).isFalse();
        assertThat(redis.hasKey(reverse(activeToken))).isFalse();
        assertThat(redis.hasKey("queue:active-token:" + activeToken)).isFalse();
        invalid(queue.status(event, user, waiting));
    }

    @AfterEach
    void cleanupFixture() {
        var deleteKeys = new ArrayList<String>();
        for (String token : redis.opsForZSet().range("queue:event:" + event + ":active", 0, -1)) {
            deleteKeys.add("queue:active-token:" + token);
            deleteKeys.add(reverse(token));
        }
        for (String token : waitingTokens) {
            deleteKeys.add("queue:waiting-token:" + token);
            deleteKeys.add(forward(token));
        }
        deleteKeys.addAll(List.of("queue:event:" + event + ":active", "queue:event:" + event + ":waiting",
                "queue:event:" + event + ":waiting-expiry", "queue:user-sessions:" + user));
        redis.delete(deleteKeys);
        waitingTokens.clear();
    }

    @Test
    void promotion_between_mapping_lookup_and_lua_requests_a_read_without_writes() {
        String waiting = enter();
        String activeToken = value(queue.status(event, user, waiting)).token();
        String candidate = UUID.randomUUID().toString();
        var keys = new QueueRedisKeys();
        StepVerifier.create(reactiveRedis.execute(QueueRedisScripts.STATUS_WITH_IDEMPOTENCY,
                List.of(keys.waitingKey(event), keys.activeKey(event), keys.activeTokenKey(candidate),
                        keys.userSessionsKey(user), keys.waitingTokenKey(waiting), keys.waitingExpiryKey(event),
                        keys.waitingActiveMappingKey(waiting), keys.activeWaitingMappingKey(candidate),
                        keys.activeTokenKey(candidate), keys.activeWaitingMappingKey(candidate)),
                List.of(waiting, candidate, event + ":" + user, "1200000", "2", "60000",
                        keys.activeSessionMember(candidate), keys.waitingSessionMember(waiting), "")))
                .expectNext("RETRY").verifyComplete();
        assertThat(redis.hasKey(keys.activeTokenKey(candidate))).isFalse();
        assertThat(redis.hasKey(keys.activeWaitingMappingKey(candidate))).isFalse();
        assertThat(value(queue.status(event, user, waiting)).token()).isEqualTo(activeToken);
    }

    @Test
    void cached_token_missing_from_the_active_index_is_not_reissued() {
        String waiting = enter();
        String activeToken = value(queue.status(event, user, waiting)).token();
        redis.opsForZSet().remove("queue:event:" + event + ":active", activeToken);
        invalid(queue.status(event, user, waiting));
        assertThat(redis.opsForZSet().zCard("queue:event:" + event + ":active")).isZero();
        // 직접 손상한 인덱스와 무관하게 테스트가 생성한 토큰·역매핑은 정리한다.
        redis.delete(List.of("queue:active-token:" + activeToken, reverse(activeToken)));
    }

    @Test
    void a_mapping_to_another_waiting_tokens_active_session_is_rejected() {
        String first = enter();
        String firstActive = value(queue.status(event, user, first)).token();
        String second = enter();
        String secondActive = value(queue.status(event, user, second)).token();
        redis.opsForValue().set(forward(first), secondActive, Duration.ofMinutes(20));
        invalid(queue.status(event, user, first));
        assertThat(value(queue.status(event, user, second)).token()).isEqualTo(secondActive);
        assertThat(redis.hasKey("queue:active-token:" + firstActive)).isTrue();
    }
    @Test
    void token_and_both_mappings_have_one_expiry_and_replays_do_not_extend_it() {
        String waiting = enter();
        String activeToken = value(queue.status(event, user, waiting)).token();
        String activeKey = "queue:active-token:" + activeToken;
        Long expiry = expiresAt(activeKey);
        assertThat(redis.opsForValue().get(forward(waiting))).isEqualTo(activeToken);
        assertThat(redis.opsForValue().get(reverse(activeToken))).isEqualTo(waiting);
        assertThat(expiresAt(forward(waiting))).isEqualTo(expiry);
        assertThat(expiresAt(reverse(activeToken))).isEqualTo(expiry);
        assertThat(redis.opsForZSet().score("queue:event:" + event + ":active", activeToken))
                .isEqualTo(expiry.doubleValue());
        var replay = value(Mono.delay(Duration.ofMillis(50)).then(queue.status(event, user, waiting)));
        assertThat(replay.token()).isEqualTo(activeToken);
        assertThat(expiresAt(activeKey)).isEqualTo(expiry);
        assertThat(expiresAt(forward(waiting))).isEqualTo(expiry);
        assertThat(expiresAt(reverse(activeToken))).isEqualTo(expiry);
    }

    @Test
    void cached_token_cannot_be_recovered_by_another_user_or_event() {
        String waiting = enter();
        String activeToken = value(queue.status(event, user, waiting)).token();
        invalid(queue.status(event, user + "-foreign", waiting));
        invalid(queue.status(event == Long.MAX_VALUE ? event - 1 : event + 1, user, waiting));
        assertThat(value(queue.status(event, user, waiting)).token()).isEqualTo(activeToken);
        assertThat(redis.opsForValue().get(forward(waiting))).isEqualTo(activeToken);
    }

    @Test
    void two_waiting_tokens_for_one_user_recover_their_own_active_tokens() {
        String first = enter();
        String second = enter();
        String firstActive = value(queue.status(event, user, first)).token();
        String secondActive = value(queue.status(event, user, second)).token();
        assertThat(firstActive).isNotEqualTo(secondActive);
        assertThat(value(queue.status(event, user, first)).token()).isEqualTo(firstActive);
        assertThat(value(queue.status(event, user, second)).token()).isEqualTo(secondActive);
        assertThat(redis.opsForZSet().zCard("queue:event:" + event + ":active")).isEqualTo(2L);
    }

    @Test
    void cancellation_after_committed_promotion_is_recovered_by_status() {
        String waiting = enter();
        var saved = new AtomicReference<String>();
        var committed = new CountDownLatch(1);
        Mono<Void> lostResponse = active.admit(event, user, waiting).flatMap(token -> {
            saved.set(token);
            committed.countDown();
            return Mono.never();
        });
        StepVerifier.create(lostResponse).then(() -> {
            try {
                assertThat(committed.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException error) {
                throw new AssertionError(error);
            }
        }).thenCancel().verify(Duration.ofSeconds(10));
        assertThat(value(queue.status(event, user, waiting)).token()).isEqualTo(saved.get());
        assertThat(redis.opsForZSet().zCard("queue:event:" + event + ":active")).isEqualTo(1L);
    }

    @Test
    void leave_removes_both_mappings() {
        String waiting = enter();
        String activeToken = value(queue.status(event, user, waiting)).token();
        assertThat(value(queue.leave(event, user, null, activeToken))).isTrue();
        mappingRemoved(waiting, activeToken);
    }

    @Test
    void administrator_removal_removes_both_mappings() {
        String waiting = enter();
        String activeToken = value(queue.status(event, user, waiting)).token();
        assertThat(value(inspect.removeToken(event, activeToken,
                QueueRedisInspectMode.ACTIVE))).isTrue();
        mappingRemoved(waiting, activeToken);
    }

    @Test
    void forced_replacement_removes_only_the_replaced_mapping() {
        String first = enter();
        String firstActive = value(queue.status(event, user, first)).token();
        String second = enter();
        String secondActive = value(queue.status(event, user, second)).token();
        assertThat(value(queue.enter(event == Long.MAX_VALUE ? event - 1 : event + 1, user, true)).status())
                .isEqualTo("WAITING");
        mappingRemoved(first, firstActive);
        assertThat(value(queue.status(event, user, second)).token()).isEqualTo(secondActive);
    }

    @Test
    void scheduled_cleanup_removes_mappings_for_expired_active_indexes() {
        String waiting = enter();
        String activeToken = value(queue.status(event, user, waiting)).token();
        redis.opsForZSet().add("queue:event:" + event + ":active", activeToken, 0);
        StepVerifier.create(queue.cleanupExpiredTokens()).expectComplete().verify(Duration.ofSeconds(10));
        mappingRemoved(waiting, activeToken);
    }

    @Test
    void redis_expiration_removes_both_mappings_and_retry_cannot_reissue_a_token() {
        String waiting = enter();
        var shortTtl = new QueueProperties();
        shortTtl.setActiveTokenTtl(Duration.ofSeconds(1));
        var shortLivedActive = new ReactiveActiveQueueService(reactiveRedis, shortTtl,
                new QueueRedisKeys(), sessions);
        String activeToken = value(shortLivedActive.admit(event, user, waiting));
        value(Mono.delay(Duration.ofMillis(1500)));
        mappingRemoved(waiting, activeToken);
        assertThat(redis.opsForZSet().zCard("queue:event:" + event + ":active")).isZero();
    }

    @Test
    void a_mapping_with_a_missing_active_token_is_not_returned_or_recreated() {
        String waiting = enter();
        String activeToken = value(queue.status(event, user, waiting)).token();
        redis.delete("queue:active-token:" + activeToken);
        invalid(queue.status(event, user, waiting));
        assertThat(redis.hasKey("queue:active-token:" + activeToken)).isFalse();
        assertThat(redis.opsForZSet().zCard("queue:event:" + event + ":active")).isEqualTo(1L);
    }

    @Test
    void event_clear_also_deletes_both_mapping_directions() {
        String waiting = enter();
        String activeToken = value(queue.status(event, user, waiting)).token();
        assertThat(value(management.clearEventQueue(event))).isEqualTo(4L);
        mappingRemoved(waiting, activeToken);
    }

    @Test
    void event_clear_rechecks_a_changed_active_snapshot_before_deleting_any_keys() {
        String waiting = enter();
        String activeToken = value(queue.status(event, user, waiting)).token();
        // 활성 목록을 비어 있다고 조회한 직후 새 승격이 발생한 상황을 재현한다.
        StepVerifier.create(reactiveRedis.execute(QueueRedisScripts.CLEAR_EVENT,
                List.of("queue:event:" + event + ":waiting", "queue:event:" + event + ":active"),
                List.of())).expectNext(-1L).verifyComplete();
        assertThat(value(queue.status(event, user, waiting)).token()).isEqualTo(activeToken);
        assertThat(value(management.clearEventQueue(event))).isEqualTo(4L);
        mappingRemoved(waiting, activeToken);
    }
    @Test
    void wrong_mapping_key_type_fails_before_promotion_writes() {
        String waiting = enter();
        redis.opsForZSet().add(forward(waiting), "잘못된 타입", 0);
        StepVerifier.create(queue.status(event, user, waiting))
                .expectError(DataAccessException.class).verify(Duration.ofSeconds(10));
        assertThat(redis.hasKey("queue:waiting-token:" + waiting)).isTrue();
        assertThat(redis.opsForZSet().zCard("queue:event:" + event + ":active")).isZero();
        assertThat(redis.opsForZSet().score("queue:user-sessions:" + user, "waiting:" + waiting)).isNotNull();
    }

    @Test
    void retry_after_lost_promotion_response_returns_the_original_active_token() {
        String waiting = enter();
        var first = value(queue.status(event, user, waiting));
        var retry = value(queue.status(event, user, waiting));
        assertThat(retry.status()).isEqualTo("READY");
        assertThat(retry.token()).isEqualTo(first.token());
        assertThat(retry.activeTokenExpiresAt()).isEqualTo(first.activeTokenExpiresAt());
        assertThat(redis.opsForZSet().zCard("queue:event:" + event + ":active")).isEqualTo(1L);
        assertThat(redis.opsForZSet().zCard("queue:user-sessions:" + user)).isEqualTo(1L);
        assertThat(redis.hasKey("queue:waiting-token:" + waiting)).isFalse();
    }

    @Test
    void concurrent_retries_return_one_active_token_without_consuming_extra_slots() {
        String waiting = enter();
        var results = value(Flux.range(0, 24).flatMap(index -> queue.status(event, user, waiting), 24).collectList());
        assertThat(results).hasSize(24);
        assertThat(results).allSatisfy(response -> assertThat(response.status()).isEqualTo("READY"));
        assertThat(results.stream().map(response -> response.token()).distinct().toList()).hasSize(1);
        assertThat(redis.opsForZSet().zCard("queue:event:" + event + ":active")).isEqualTo(1L);
    }

    @Test
    void complete_revokes_replay_and_does_not_issue_a_new_token() {
        String waiting = enter();
        String active = value(queue.status(event, user, waiting)).token();
        assertThat(value(queue.complete(event, user, active))).isTrue();
        mappingRemoved(waiting, active);
        StepVerifier.create(queue.status(event, user, waiting))
                .expectError(QueueTokenInvalidException.class).verify(Duration.ofSeconds(10));
        assertThat(redis.opsForZSet().zCard("queue:event:" + event + ":active")).isZero();
    }
}