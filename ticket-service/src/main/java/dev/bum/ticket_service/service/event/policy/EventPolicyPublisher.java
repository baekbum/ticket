package dev.bum.ticket_service.service.event.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.ticket_service.jpa.event.policy.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class EventPolicyPublisher {
    private final EventPolicyOutboxJpaRepository repository;
    private final KafkaTemplate<Object, Object> kafka;
    private final ObjectMapper mapper;
    @Value("${topic.event-policy.name:event-queue-policy}") private String topic;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(Long id, boolean replay) {
        EventPolicyOutbox row = repository.findForUpdate(id).orElse(null);

        if (row == null || (row.isPublished() && !replay)) return;

        try {
            kafka.send(topic, row.getEventId().toString(), mapper.writeValueAsString(row.message()))
                    .get(5, TimeUnit.SECONDS);
            row.delivered();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            row.failed();
        } catch (Exception e) {
            row.failed();
        }
    }
}
