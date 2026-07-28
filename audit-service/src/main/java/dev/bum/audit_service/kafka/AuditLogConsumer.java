package dev.bum.audit_service.kafka;

import dev.bum.audit_service.kafka.event.AuditLogEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuditLogConsumer {

    @KafkaListener(
            topics = "${topic.audit.log.name}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(AuditLogEvent event) {
        log.info(
                "Received audit log event. serviceName={}, action={}, targetType={}, targetId={}, result={}, traceId={}",
                event.getServiceName(),
                event.getAction(),
                event.getTargetType(),
                event.getTargetId(),
                event.getResult(),
                event.getTraceId()
        );
    }
}
