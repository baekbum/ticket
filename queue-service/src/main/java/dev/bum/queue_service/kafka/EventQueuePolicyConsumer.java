package dev.bum.queue_service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.kafka.event.EventQueuePolicyEvent;
import dev.bum.queue_service.service.reactive.EventQueuePolicyStore;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class EventQueuePolicyConsumer {

    private final EventQueuePolicyStore policies;
    private final ObjectMapper mapper;

    @KafkaListener(topics = "${topic.event-policy.name:event-queue-policy}",
            groupId = "${topic.event-policy.group-id:queue-event-policy}")
    public void consume(String json) throws Exception {

        // Kafka 소비 스레드에서 Redis 반영 완료까지 기다린다. Netty 이벤트 루프에서는 block하지 않는다.
        policies.apply(mapper.readValue(json, EventQueuePolicyEvent.class)).block(Duration.ofSeconds(5));
    }
}
