package dev.bum.queue_service.service.reactive;

import dev.bum.common.kafka.event.EventQueuePolicyEvent;
import dev.bum.common.service.queue.dto.QueueValidateRequest;
import dev.bum.queue_service.QueuePolicyFixture;
import dev.bum.queue_service.QueueServiceApplication;
import dev.bum.queue_service.exception.EventPolicyException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.time.Duration;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "QUEUE_TEST_REDIS_PORT", matches = "\\d+")
@SpringBootTest(classes = QueueServiceApplication.class, properties = {
        "spring.profiles.active=baseline", "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=${QUEUE_TEST_REDIS_PORT}", "spring.data.redis.database=3",
        "token-state.redis.host=127.0.0.1", "token-state.redis.port=${QUEUE_TEST_REDIS_PORT}",
        "token-state.redis.database=0", "app.queue.cleanup-mode=disabled",
        "token.secret=queue-baseline-only-secret-at-least-32-bytes-long"})
class EventQueuePolicyIntegrationTest {
    @Autowired EventQueuePolicyStore policies;
    @Autowired ReactiveQueueService queue;
    @Autowired StringRedisTemplate redis;
    private long event;
    private String user;
    @BeforeEach void fixture() {
        event = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        user = "policy-" + UUID.randomUUID();
    }
    private <T> T value(Mono<T> request) { return request.block(Duration.ofSeconds(5)); }
    private String key() { return "queue:event:" + event + ":policy"; }
    private EventQueuePolicyEvent snapshot(long version, String state, long end, int capacity) {
        long now = System.currentTimeMillis();
        return new EventQueuePolicyEvent(event, version, state, now - 60000, end, now + 7200000, capacity);
    }
    @Test void missing_policy_does_not_register_a_waiter() {
        StepVerifier.create(queue.enter(event, user, false)).expectError(EventPolicyException.class).verify();
        assertThat(redis.hasKey("queue:event:" + event + ":waiting")).isFalse();
    }
    @Test void older_and_duplicate_messages_do_not_resurrect_a_deleted_event() {
        long end = System.currentTimeMillis() + 3600000;
        assertThat(value(policies.apply(snapshot(2, "CANCELLED", end, 1)))).isEqualTo(1L);
        assertThat(value(policies.apply(snapshot(1, "ON_SALE", end, 100)))).isZero();
        assertThat(value(policies.apply(snapshot(2, "ON_SALE", end, 100)))).isZero();
        assertThat(redis.opsForHash().get(key(), "status")).isEqualTo("CANCELLED");
        value(policies.apply(snapshot(3, "DELETED", end, 1)));
        value(policies.apply(snapshot(2, "ON_SALE", end, 1)));
        StepVerifier.create(queue.enter(event, user, false)).expectError(EventPolicyException.class).verify();
        redis.delete(key());
    }
    @Test void sales_end_blocks_promotion_active_replay_and_validation_but_allows_completion() {
        value(policies.apply(snapshot(1, "ON_SALE", System.currentTimeMillis() + 3600000, 1)));
        String first = value(queue.enter(event, user, false)).token();
        String active = value(queue.status(event, user, first)).token();
        String second = value(queue.enter(event, user + "-second", false)).token();
        value(policies.apply(snapshot(2, "ON_SALE", System.currentTimeMillis() - 1, 1)));
        StepVerifier.create(queue.status(event, user, first)).expectError(EventPolicyException.class).verify();
        StepVerifier.create(queue.status(event, user + "-second", second)).expectError(EventPolicyException.class).verify();
        StepVerifier.create(queue.validate(new QueueValidateRequest(event, user, active)))
                .expectError(EventPolicyException.class).verify();
        assertThat(value(queue.complete(event, user, active))).isTrue();
        value(queue.leave(event, user + "-second", second, null));
        redis.delete(key());
    }
    @Test void lowering_capacity_does_not_evict_existing_users() {
        long end = System.currentTimeMillis() + 3600000;
        value(policies.apply(snapshot(1, "ON_SALE", end, 2)));
        String a = value(queue.enter(event, user + "a", false)).token();
        String b = value(queue.enter(event, user + "b", false)).token();
        String c = value(queue.enter(event, user + "c", false)).token();
        String activeA = value(queue.status(event, user + "a", a)).token();
        String activeB = value(queue.status(event, user + "b", b)).token();
        value(policies.apply(snapshot(2, "ON_SALE", end, 1)));
        assertThat(value(queue.status(event, user + "c", c)).status()).isEqualTo("WAITING");
        assertThat(value(queue.status(event, user + "a", a)).token()).isEqualTo(activeA);
        assertThat(value(queue.complete(event, user + "a", activeA))).isTrue();
        assertThat(value(queue.status(event, user + "c", c)).status()).isEqualTo("WAITING");
        value(queue.complete(event, user + "b", activeB));
        String activeC = value(queue.status(event, user + "c", c)).token();
        value(queue.complete(event, user + "c", activeC));
        redis.delete(key());
    }
    @Test void identical_snapshot_restores_policy_after_redis_data_loss() {
        EventQueuePolicyEvent message = QueuePolicyFixture.event(event, 3);
        value(policies.apply(message));
        redis.delete(key());
        assertThat(value(policies.apply(message))).isEqualTo(1L);
        policies.validate(event, false).block(Duration.ofSeconds(5));
        redis.delete(key());
    }
}
