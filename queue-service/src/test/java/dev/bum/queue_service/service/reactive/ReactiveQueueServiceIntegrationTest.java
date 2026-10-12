package dev.bum.queue_service.service.reactive;

import dev.bum.common.service.queue.dto.*;
import dev.bum.common.service.queue.enums.QueueRedisInspectMode;
import dev.bum.queue_service.QueueServiceApplication;
import dev.bum.queue_service.exception.QueueTokenInvalidException;
import dev.bum.queue_service.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.*;
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
class ReactiveQueueServiceIntegrationTest {
    @Autowired ReactiveQueueService queue;
    @Autowired ReactiveWaitingQueueService waiting;
    @Autowired ReactiveActiveQueueService active;
    @Autowired ReactiveUserQueueSessionService sessions;
    @Autowired ReactiveQueueAdminService admin;
    @Autowired ReactiveQueueRedisInspectService inspect;
    @Autowired ReactiveQueueTestManagementService management;
    @Autowired QueueService original;
    @Autowired QueueAdminService originalAdmin;
    @Autowired QueueRedisInspectService originalInspect;
    @Autowired StringRedisTemplate redis;
    private long event;
    private String user;

    @BeforeEach
    void fixture() {
        event = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        user = newUser();
    }

    private String newUser() { return "reactive-test-" + UUID.randomUUID(); }

    private <T> T value(Mono<T> publisher) {
        AtomicReference<T> result = new AtomicReference<>();
        StepVerifier.create(publisher).assertNext(result::set).expectComplete().verify(Duration.ofSeconds(10));
        return result.get();
    }

    private void complete(Mono<Void> publisher) {
        StepVerifier.create(publisher).expectComplete().verify(Duration.ofSeconds(10));
    }

    private String enter(String userId) {
        var response = value(queue.enter(event, userId, false));
        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(response.token()).isNotBlank();
        return response.token();
    }

    private String admit(String userId) {
        var response = value(queue.status(event, userId, enter(userId)));
        assertThat(response.status()).isEqualTo("READY");
        return response.token();
    }

    @Test
    void enter_is_lazy_and_each_subscription_creates_its_own_token() {
        Mono<QueueEnterResponse> registration = queue.enter(event, user, false);
        assertThat(redis.opsForZSet().zCard("queue:event:" + event + ":waiting")).isZero();
        var first = value(registration);
        var second = value(registration);
        assertThat(first.token()).isNotEqualTo(second.token());
        assertThat(first.expiresInSeconds()).isEqualTo(60L);
        assertThat(first.rank()).isEqualTo(1L);
        assertThat(second.rank()).isEqualTo(2L);
        assertThat(redis.opsForZSet().zCard("queue:user-sessions:" + user)).isEqualTo(2L);
    }

    @Test
    void lifecycle_accepts_existing_mvc_tokens_and_preserves_redis_transitions() {
        String waitingToken = original.enter(event, user, false).token();
        var ready = value(queue.status(event, user, waitingToken));
        assertThat(ready.status()).isEqualTo("READY");
        assertThat(ready.rank()).isZero();
        assertThat(ready.activeTokenExpiresAt()).isNotNull();
        assertThat(redis.hasKey("queue:waiting-token:" + waitingToken)).isFalse();
        assertThat(redis.opsForZSet().score("queue:event:" + event + ":waiting-expiry", waitingToken)).isNull();
        assertThat(redis.opsForZSet().score("queue:user-sessions:" + user, "waiting:" + waitingToken)).isNull();
        assertThat(redis.opsForZSet().score("queue:user-sessions:" + user, "active:" + ready.token())).isNotNull();
        var request = new QueueValidateRequest(event, user, ready.token());
        assertThat(value(queue.validate(request))).isEqualTo(original.validate(request));
        assertThat(value(queue.complete(event, user, ready.token()))).isTrue();
        assertThat(value(queue.complete(event, user, ready.token()))).isFalse();
        assertThat(value(queue.validate(request)).reason()).isEqualTo("INVALID_QUEUE_TOKEN");
        assertThat(redis.hasKey("queue:active-token:" + ready.token())).isFalse();
        assertThat(redis.opsForZSet().zCard("queue:user-sessions:" + user)).isZero();
        StepVerifier.create(queue.status(event, user, waitingToken))
                .expectError(QueueTokenInvalidException.class).verify(Duration.ofSeconds(10));
    }

    @Test
    void mvc_service_can_promote_a_reactive_waiting_token() {
        String token = enter(user);
        var ready = original.status(event, user, token);
        assertThat(ready.status()).isEqualTo("READY");
        assertThat(value(queue.validate(new QueueValidateRequest(event, user, ready.token()))).allowed()).isTrue();
    }

    @Test
    void full_capacity_keeps_waiting_state_and_refreshes_indexes() {
        admit(newUser());
        admit(newUser());
        String token = enter(user);
        var response = value(queue.status(event, user, token));
        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(response.rank()).isEqualTo(1L);
        assertThat(response.waitingCount()).isEqualTo(1L);
        assertThat(response.token()).isEqualTo(token);
        assertThat(response.estimatedEntryAt()).isNotNull();
        assertThat(response.activeTokenExpiresAt()).isEqualTo(response.estimatedEntryAt().plus(Duration.ofMinutes(20)));
        assertThat(redis.getExpire("queue:waiting-token:" + token)).isPositive();
        assertThat(redis.opsForZSet().score("queue:event:" + event + ":waiting-expiry", token)).isNotNull();
    }

    @Test
    void session_limit_and_forced_waiting_replacement_match_existing_behavior() {
        String first = enter(user);
        enter(user);
        var limited = value(queue.enter(event, user, false));
        assertThat(limited.status()).isEqualTo("SESSION_LIMIT_CONFIRM_REQUIRED");
        assertThat(limited.token()).isNull();
        var forced = value(queue.enter(event, user, true));
        assertThat(forced.status()).isEqualTo("WAITING");
        assertThat(redis.hasKey("queue:waiting-token:" + first)).isFalse();
        assertThat(redis.opsForZSet().zCard("queue:user-sessions:" + user)).isEqualTo(2L);
    }

    @Test
    void force_can_replace_an_active_session_when_it_expires_first() {
        String first = admit(user);
        admit(user);
        var forced = value(queue.enter(event, user, true));
        assertThat(forced.status()).isEqualTo("WAITING");
        assertThat(redis.hasKey("queue:active-token:" + first)).isFalse();
        assertThat(redis.opsForZSet().zCard("queue:event:" + event + ":active")).isEqualTo(1L);
        assertThat(redis.opsForZSet().zCard("queue:user-sessions:" + user)).isEqualTo(2L);
    }

    @Test
    void leave_supports_waiting_and_active_tokens_and_rejects_other_users() {
        String waitingToken = enter(user);
        assertThat(value(queue.leave(event, newUser(), waitingToken, null))).isFalse();
        assertThat(redis.hasKey("queue:waiting-token:" + waitingToken)).isTrue();
        assertThat(value(queue.leave(event, user, waitingToken, null))).isTrue();
        assertThat(value(queue.leave(event, user, waitingToken, null))).isFalse();
        String activeToken = admit(user);
        assertThat(value(queue.leave(event, user, null, activeToken))).isTrue();
        assertThat(redis.opsForZSet().zCard("queue:user-sessions:" + user)).isZero();
    }

    @Test
    void invalid_identity_and_token_errors_arrive_on_subscription() {
        Mono<QueueEnterResponse> invalidUser = queue.enter(event, " ", false);
        StepVerifier.create(invalidUser).expectError(IllegalArgumentException.class).verify(Duration.ofSeconds(10));
        String token = enter(user);
        StepVerifier.create(queue.status(event, newUser(), token))
                .expectError(QueueTokenInvalidException.class).verify(Duration.ofSeconds(10));
        StepVerifier.create(queue.status(event, user, null))
                .expectError(QueueTokenInvalidException.class).verify(Duration.ofSeconds(10));
        assertThat(value(queue.complete(event, user, "missing"))).isFalse();
        assertThat(value(queue.validate(new QueueValidateRequest(event, newUser(), token))).allowed()).isFalse();
    }

    @Test
    void session_pruning_removes_missing_tokens_and_handles_empty_sessions() {
        String token = enter(user);
        String stale = "stale-" + UUID.randomUUID();
        redis.opsForZSet().add("queue:user-sessions:" + user, "waiting:" + stale, 0);
        complete(sessions.prune(user));
        assertThat(value(sessions.count(user))).isEqualTo(1L);
        assertThat(value(sessions.firstExpiringSession(user)).token()).isEqualTo(token);
        StepVerifier.create(sessions.activeSession(user)).expectComplete().verify(Duration.ofSeconds(10));
        complete(sessions.prune(newUser()));
    }

    @Test
    void scan_cleanup_removes_expired_waiting_and_active_event_entries() {
        String expiredUser = newUser();
        String expiredActive = admit(expiredUser);
        String expiredWaiting = enter(user);
        redis.opsForZSet().add("queue:event:" + event + ":active", expiredActive, 0);
        redis.opsForZSet().add("queue:event:" + event + ":waiting-expiry", expiredWaiting, 0);
        // 마침표가 포함된 잘못된 이벤트 ID도 SCAN 결과에서 안전하게 건너뛴다.
        redis.opsForZSet().add("queue:event:invalid.id:waiting-expiry", "ignored", 0);
        complete(queue.cleanupExpiredTokens());
        assertThat(redis.hasKey("queue:active-token:" + expiredActive)).isFalse();
        assertThat(redis.hasKey("queue:waiting-token:" + expiredWaiting)).isFalse();
        assertThat(redis.opsForZSet().zCard("queue:event:" + event + ":active")).isZero();
        assertThat(redis.opsForZSet().zCard("queue:event:" + event + ":waiting")).isZero();
        assertThat(redis.opsForZSet().zCard("queue:user-sessions:" + expiredUser)).isZero();
        // 스케줄러의 waiting 정리는 userId를 모르므로 사용자 인덱스는 prune에서 정리한다.
        complete(sessions.prune(user));
        assertThat(value(sessions.count(user))).isZero();
        redis.delete("queue:event:invalid.id:waiting-expiry");
    }

    @Test
    void admin_bulk_preserves_input_order_and_reuses_active_tokens() {
        String activeToken = admit(user);
        admit(newUser());
        String waitingUser = newUser();
        String waitingToken = enter(waitingUser);
        List<String> users = List.of(waitingUser, user);
        Map<String, String> tokens = Map.of(waitingUser, waitingToken);
        var actual = value(admin.statuses(event, users, tokens));
        var expected = originalAdmin.statuses(event, users, tokens);
        assertThat(actual).extracting(QueueStatusResponse::status).containsExactly("WAITING", "READY");
        assertThat(actual).extracting(QueueStatusResponse::token)
                .containsExactlyElementsOf(expected.stream().map(QueueStatusResponse::token).toList());
        assertThat(actual.get(1).token()).isEqualTo(activeToken);
    }

    @Test
    void inspection_preserves_missing_values_negative_ttl_and_removal_results() {
        String token = enter(user);
        var actual = value(inspect.inspectEventQueue(event, null, 0));
        var expected = originalInspect.inspectEventQueue(event, null, 0);
        assertThat(actual.getLimit()).isEqualTo(100);
        assertThat(actual.getEntries().getFirst().getValue()).isEqualTo(expected.getEntries().getFirst().getValue());
        assertThat(actual.getEntries().getFirst().getRank()).isEqualTo(1L);
        redis.delete("queue:waiting-token:" + token);
        var orphan = value(inspect.inspectEventQueue(event, QueueRedisInspectMode.WAITING, 1000));
        assertThat(orphan.getLimit()).isEqualTo(500);
        assertThat(orphan.getEntries()).hasSize(1);
        assertThat(orphan.getEntries().getFirst().getValue()).isNull();
        assertThat(orphan.getEntries().getFirst().getTtlSeconds()).isEqualTo(-2L);
        var missing = value(inspect.inspectToken("missing"));
        assertThat(missing).isEqualTo(originalInspect.inspectToken("missing"));
        assertThat(value(inspect.removeToken(event, token, QueueRedisInspectMode.WAITING))).isTrue();
        assertThat(value(inspect.removeToken(event, token, QueueRedisInspectMode.WAITING))).isFalse();
        assertThat(value(inspect.removeToken(event, token, QueueRedisInspectMode.TOKEN))).isFalse();
    }

    @Test
    void active_inspection_removal_also_removes_user_session() {
        String token = admit(user);
        var state = value(inspect.inspectEventQueue(event, QueueRedisInspectMode.ACTIVE, 10));
        assertThat(state.getEntries()).hasSize(1);
        assertThat(state.getEntries().getFirst().getToken()).isEqualTo(token);
        assertThat(state.getEntries().getFirst().getRank()).isNull();
        assertThat(value(inspect.removeToken(event, token, QueueRedisInspectMode.ACTIVE))).isTrue();
        assertThat(redis.opsForZSet().zCard("queue:user-sessions:" + user)).isZero();
    }

    @Test
    void test_management_deletes_event_keys_and_admission_mappings() {
        String activeToken = admit(user);
        enter(newUser());
        assertThat(value(management.clearEventQueue(event))).isEqualTo(5L);
        assertThat(redis.hasKey("queue:event:" + event + ":active")).isFalse();
        assertThat(redis.hasKey("queue:event:" + event + ":waiting")).isFalse();
        assertThat(redis.hasKey("queue:active-token:" + activeToken)).isFalse();
    }

    @Test
    void concurrent_admission_preserves_capacity_with_real_lua() {
        List<String> users = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String id = newUser();
            users.add(id);
            tokens.add(enter(id));
        }
        var results = value(Flux.range(0, users.size())
                .flatMap(index -> queue.status(event, users.get(index), tokens.get(index)), 12).collectList());
        assertThat(results.stream().filter(response -> response.status().equals("READY")).count()).isEqualTo(2);
        assertThat(results.stream().filter(response -> response.status().equals("WAITING")).count()).isEqualTo(10);
        assertThat(redis.opsForZSet().zCard("queue:event:" + event + ":active")).isEqualTo(2L);
    }
}
