package dev.bum.audit_service.kafka;

import dev.bum.audit_service.audit.AuditLogEntity;
import dev.bum.audit_service.audit.AuditLogPersistenceService;
import dev.bum.audit_service.kafka.event.AuditLogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogConsumer {

    private final AuditLogPersistenceService persistenceService;

    @KafkaListener(
            topics = "${topic.audit.log.name}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(AuditLogEvent event) {
        AuditLogEntity savedAuditLog = persistenceService.save(event);

        log.info(
                "Saved audit log. id={}, serviceName={}, action={}, targetType={}, targetId={}, result={}, traceId={}",
                savedAuditLog.getId(),
                event.getServiceName(),
                event.getAction(),
                event.getTargetType(),
                event.getTargetId(),
                event.getResult(),
                event.getTraceId()
        );
    }
}
