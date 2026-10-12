package dev.bum.queue_service.service.reactive;

import dev.bum.common.service.queue.dto.QueueEnterResponse;
import dev.bum.queue_service.QueueServiceApplication;
import dev.bum.queue_service.exception.QueueTokenInvalidException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.UUID;
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
class ReactiveQueueConcurrencyTest {
    @Autowired ReactiveQueueService queue;
    @Autowired ReactiveWaitingQueueService waiting;
    @Autowired ReactiveActiveQueueService active;
    @Autowired StringRedisTemplate redis;
    private long event;
    private String user;

    @BeforeEach
    void fixture() {
        event = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        user = "race-" + UUID.randomUUID();
    }

    @AfterEach
    void cleanupFixture() {
        // 오류 재현용 키가 다른 테스트의 전체 이벤트 정리에 섞이지 않게 한다.
        redis.delete(java.util.List.of("queue:event:" + event + ":waiting",
                "queue:event:" + event + ":waiting-expiry", "queue:event:" + event + ":active",
                "queue:user-sessions:" + user));
    }
    private <T> T value(Mono<T> publisher) {
        AtomicReference<T> result = new AtomicReference<>();
        StepVerifier.create(publisher).assertNext(result::set).expectComplete().verify(Duration.ofSeconds(10));
        return result.get();
    }

    @Test
    void concurrent_status_consumes_a_waiting_token_only_once() {
        String token = value(queue.enter(event, user, false)).token();
        var results = value(Flux.range(0, 24).flatMap(index -> queue.status(event, user, token), 24).collectList());
        assertThat(results).hasSize(24);
        assertThat(results).allSatisfy(response -> assertThat(response.status()).isEqualTo("READY"));
        assertThat(results.stream().map(response -> response.token()).distinct().toList()).hasSize(1);        assertThat(redis.opsForZSet().zCard("queue:event:" + event + ":active")).isEqualTo(1L);
        assertThat(redis.opsForZSet().zCard("queue:user-sessions:" + user)).isEqualTo(1L);
        assertThat(redis.hasKey("queue:waiting-token:" + token)).isFalse();
        assertThat(redis.opsForZSet().score("queue:event:" + event + ":waiting-expiry", token)).isNull();
    }

    @Test
    void forced_registration_removes_an_active_session_from_its_original_event() {
        String firstWaiting = value(queue.enter(event, user, false)).token();
        String firstActive = value(queue.status(event, user, firstWaiting)).token();
        String secondWaiting = value(queue.enter(event, user, false)).token();
        value(queue.status(event, user, secondWaiting));
        long nextEvent = event == Long.MAX_VALUE ? event - 1 : event + 1;
        assertThat(value(queue.enter(nextEvent, user, true)).status()).isEqualTo("WAITING");
        assertThat(redis.opsForZSet().score("queue:event:" + event + ":active", firstActive)).isNull();
        assertThat(redis.hasKey("queue:active-token:" + firstActive)).isFalse();
        assertThat(redis.opsForZSet().zCard("queue:user-sessions:" + user)).isEqualTo(2L);
    }
    @Test
    void wrong_key_type_rejects_registration_without_partial_writes() {
        redis.opsForValue().set("queue:event:" + event + ":waiting-expiry", "잘못된 타입");
        StepVerifier.create(waiting.createWaiting(event, user))
                .expectError(org.springframework.dao.DataAccessException.class).verify(Duration.ofSeconds(5));
        assertThat(redis.hasKey("queue:event:" + event + ":waiting")).isFalse();
        assertThat(redis.hasKey("queue:user-sessions:" + user)).isFalse();
        assertThat(redis.keys("queue:waiting-token:*").stream()
                .map(key -> redis.opsForValue().get(key)).filter((event + ":" + user)::equals)).isEmpty();
    }

    @Test
    void failed_cleanup_keeps_expiry_index_for_retry() {
        String token = value(queue.enter(event, user, false)).token();
        String waitingKey = "queue:event:" + event + ":waiting";
        String expiryKey = "queue:event:" + event + ":waiting-expiry";
        redis.opsForZSet().add(expiryKey, token, 0);
        redis.delete(waitingKey);
        redis.opsForValue().set(waitingKey, "잘못된 타입");
        StepVerifier.create(waiting.pruneExpiredWaitingTokens(event))
                .expectError(org.springframework.dao.DataAccessException.class).verify(Duration.ofSeconds(5));
        assertThat(redis.opsForZSet().score(expiryKey, token)).isEqualTo(0.0);
        assertThat(redis.hasKey("queue:waiting-token:" + token)).isTrue();
        redis.delete(waitingKey);
        redis.opsForZSet().add(waitingKey, token, 0);
        StepVerifier.create(waiting.pruneExpiredWaitingTokens(event)).verifyComplete();
        assertThat(redis.opsForZSet().score(expiryKey, token)).isNull();
        assertThat(redis.hasKey("queue:waiting-token:" + token)).isFalse();
        assertThat(redis.opsForZSet().zCard("queue:user-sessions:" + user)).isZero();
    }

    @Test
    void cleanup_does_not_remove_a_token_refreshed_after_the_expired_scan() {
        String token = value(queue.enter(event, user, false)).token();
        String expiryKey = "queue:event:" + event + ":waiting-expiry";
        redis.opsForZSet().add(expiryKey, token, 0);
        long cutoff = System.currentTimeMillis();
        StepVerifier.create(waiting.refreshWaitingToken(event, user, token)).verifyComplete();
        var template = new org.springframework.data.redis.core.ReactiveStringRedisTemplate(
                (org.springframework.data.redis.connection.ReactiveRedisConnectionFactory) redis.getConnectionFactory());
        StepVerifier.create(template.execute(dev.bum.queue_service.service.QueueRedisScripts.REMOVE_TOKEN,
                java.util.List.of("queue:event:" + event + ":waiting", expiryKey,
                        "queue:waiting-token:" + token, "queue:user-sessions:" + user),
                java.util.List.of(token, "waiting:" + token, String.valueOf(cutoff), "1")))
                .expectNext(0L).verifyComplete();
        assertThat(redis.hasKey("queue:waiting-token:" + token)).isTrue();
        assertThat(redis.opsForZSet().score(expiryKey, token)).isGreaterThan((double) cutoff);
    }

    @Test
    void cancellation_after_registration_keeps_a_complete_expiring_session() {
        AtomicReference<String> saved = new AtomicReference<>();
        var registered = new java.util.concurrent.CountDownLatch(1);
        Mono<Void> lostResponse = waiting.createWaiting(event, user).flatMap(token -> {
            saved.set(token);
            registered.countDown();
            return Mono.never();
        });
        StepVerifier.create(lostResponse).then(() -> {
            try {
                assertThat(registered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException error) {
                throw new AssertionError(error);
            }
        }).thenCancel().verify(Duration.ofSeconds(10));
        String token = saved.get();
        assertThat(redis.opsForValue().get("queue:waiting-token:" + token)).isEqualTo(event + ":" + user);
        assertThat(redis.getExpire("queue:waiting-token:" + token)).isPositive();
        assertThat(redis.opsForZSet().score("queue:event:" + event + ":waiting", token)).isNotNull();
        assertThat(redis.opsForZSet().score("queue:event:" + event + ":waiting-expiry", token)).isNotNull();
        assertThat(redis.opsForZSet().score("queue:user-sessions:" + user, "waiting:" + token)).isNotNull();
    }
    @Test
    void concurrent_registration_respects_user_session_limit() {
        var results = value(Flux.range(0, 24).flatMap(index -> queue.enter(event, user, false), 24).collectList());
        assertThat(results.stream().filter(response -> "WAITING".equals(response.status())).count()).isEqualTo(2);
        assertThat(results.stream().filter(response -> "SESSION_LIMIT_CONFIRM_REQUIRED".equals(response.status())).count())
                .isEqualTo(22);
        assertThat(redis.opsForZSet().zCard("queue:user-sessions:" + user)).isEqualTo(2L);
        assertThat(redis.opsForZSet().zCard("queue:event:" + event + ":waiting")).isEqualTo(2L);
    }

    @Test
    void refresh_after_promotion_cannot_resurrect_waiting_token() {
        String token = value(queue.enter(event, user, false)).token();
        var ready = value(queue.status(event, user, token));
        assertThat(ready.status()).isEqualTo("READY");
        StepVerifier.create(waiting.refreshWaitingToken(event, user, token))
                .expectError(QueueTokenInvalidException.class).verify(Duration.ofSeconds(5));
        assertThat(redis.hasKey("queue:waiting-token:" + token)).isFalse();
        assertThat(redis.opsForZSet().score("queue:user-sessions:" + user, "waiting:" + token)).isNull();
        assertThat(redis.opsForZSet().score("queue:event:" + event + ":waiting-expiry", token)).isNull();
    }

    @Test
    void forced_registration_removes_the_original_event_indexes() {
        String first = value(queue.enter(event, user, false)).token();
        value(queue.enter(event, user, false));
        long nextEvent = event == Long.MAX_VALUE ? event - 1 : event + 1;
        QueueEnterResponse replacement = value(queue.enter(nextEvent, user, true));
        assertThat(replacement.status()).isEqualTo("WAITING");
        assertThat(redis.opsForZSet().score("queue:event:" + event + ":waiting", first)).isNull();
        assertThat(redis.opsForZSet().score("queue:event:" + event + ":waiting-expiry", first)).isNull();
        assertThat(redis.hasKey("queue:waiting-token:" + first)).isFalse();
        assertThat(redis.opsForZSet().zCard("queue:user-sessions:" + user)).isEqualTo(2L);
    }
}
