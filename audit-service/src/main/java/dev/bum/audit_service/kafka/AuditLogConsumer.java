package dev.bum.audit_service.kafka;

import dev.bum.audit_service.audit.AuditLogSaveResult;
import dev.bum.audit_service.audit.AuditLogPersistenceService;
import dev.bum.common.kafka.audit.AuditLogEvent;
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
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "auditLogKafkaListenerContainerFactory"
    )
    public void consume(AuditLogEvent event) {
        AuditLogSaveResult result = persistenceService.save(event);

        if (result == AuditLogSaveResult.DUPLICATE) {
            log.info("중복 감사 이벤트 저장 생략: serviceName={}, idempotencyKey={}",
                    event.getServiceName(), event.getIdempotencyKey());
            return;
        }

        log.info(
                "Saved audit log. idempotencyKey={}, serviceName={}, action={}, targetType={}, targetId={}, result={}, traceId={}",
                event.getIdempotencyKey(),
                event.getServiceName(),
                event.getAction(),
                event.getTargetType(),
                event.getTargetId(),
                event.getResult(),
                event.getTraceId()
        );
    }
}
