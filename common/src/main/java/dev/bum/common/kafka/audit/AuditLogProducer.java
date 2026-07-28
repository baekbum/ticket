package dev.bum.common.kafka.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogProducer {

    private final KafkaTemplate<String, AuditLogEvent> kafkaTemplate;

    @Value("${topic.audit.log.name}")
    private String auditLogTopicName;

    public void send(AuditLogEvent event) {
        kafkaTemplate.send(auditLogTopicName, keyOf(event), event)
                .whenComplete((result, throwable) -> {
                    if (throwable == null) {
                        log.info(
                                "Audit log event sent. action={}, traceId={}",
                                event.getAction(),
                                event.getTraceId()
                        );
                    } else {
                        log.warn(
                                "Audit log event failed. action={}, traceId={}",
                                event.getAction(),
                                event.getTraceId(),
                                throwable
                        );
                    }
                });
    }

    private String keyOf(AuditLogEvent event) {
        if (StringUtils.hasText(event.getTraceId())) {
            return event.getTraceId();
        }

        return event.getRequestId();
    }
}
