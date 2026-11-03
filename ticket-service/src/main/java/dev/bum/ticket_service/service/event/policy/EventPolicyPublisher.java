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

    // 공연 정책 데이터를 조회하고 저장하는 레포지토리.
    private final EventPolicyOutboxJpaRepository repository;
    // 이벤트를 Kafka 토픽으로 발행하는 클라이언트.
    private final KafkaTemplate<Object, Object> kafka;
    // 요청과 이벤트를 JSON으로 변환하는 직렬화 도구.
    private final ObjectMapper mapper;
    // Kafka 토픽 이름.
    @Value("${topic.event-policy.name:event-queue-policy}") private String topic;

    /**
     * 공연 처리에서 후속 처리 대상으로 전달한다.
     */
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
