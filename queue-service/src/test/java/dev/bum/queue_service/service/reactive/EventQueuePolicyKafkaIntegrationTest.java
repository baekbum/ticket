package dev.bum.queue_service.service.reactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.kafka.event.EventQueuePolicyEvent;
import dev.bum.queue_service.QueuePolicyFixture;
import dev.bum.queue_service.QueueServiceApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@EnabledIfEnvironmentVariable(named = "EVENT_POLICY_TEST_KAFKA", matches = ".+")
@SpringBootTest(classes = QueueServiceApplication.class, properties = {
        "spring.profiles.active=baseline", "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=${QUEUE_TEST_REDIS_PORT}", "spring.data.redis.database=3",
        "token-state.redis.host=127.0.0.1", "token-state.redis.port=${QUEUE_TEST_REDIS_PORT}",
        "token-state.redis.database=0", "app.queue.cleanup-mode=disabled",
        "token.secret=queue-baseline-only-secret-at-least-32-bytes-long",
        "spring.kafka.bootstrap-servers=${EVENT_POLICY_TEST_KAFKA}",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "topic.event-policy.name=event-policy-integration-test"})
class EventQueuePolicyKafkaIntegrationTest {
    @Autowired KafkaTemplate<Object, Object> kafka;
    @Autowired ObjectMapper mapper;
    @Autowired StringRedisTemplate redis;
    @Autowired EventQueuePolicyStore policies;
    @Autowired KafkaListenerEndpointRegistry listeners;

    @Test void broker_messages_are_applied_to_redis_and_old_versions_cannot_reopen_sales() throws Exception {
        await().atMost(Duration.ofSeconds(60)).until(() -> listeners.getListenerContainers().stream()
                .anyMatch(container -> container.getAssignedPartitions() != null
                        && !container.getAssignedPartitions().isEmpty()));
        long id = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        String key = "queue:event:" + id + ":policy";
        EventQueuePolicyEvent open = QueuePolicyFixture.event(id, 7);
        try {
            send(open);
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                    assertThat(redis.opsForHash().get(key, "admissionSize")).isEqualTo("7"));
            policies.validate(id, false).block(Duration.ofSeconds(5));
            send(new EventQueuePolicyEvent(id, 2, "CANCELLED", open.saleStartAt(), open.saleEndAt(),
                    open.eventDateTime(), 7));
            send(open);
            send(new EventQueuePolicyEvent(id, 3, "CANCELLED", open.saleStartAt(), open.saleEndAt(),
                    open.eventDateTime(), 7));
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                assertThat(redis.opsForHash().get(key, "version")).isEqualTo("3");
                assertThat(redis.opsForHash().get(key, "status")).isEqualTo("CANCELLED");
            });
        } finally { redis.delete(key); }
    }
    private void send(EventQueuePolicyEvent event) throws Exception {
        kafka.send("event-policy-integration-test", event.eventId().toString(), mapper.writeValueAsString(event))
                .get(15, TimeUnit.SECONDS);
    }
}
