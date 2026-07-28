package dev.bum.common.kafka.audit;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "topic.audit.log.name")
public class AuditLogProducer {

    private final KafkaTemplate<String, AuditLogEvent> kafkaTemplate;

    @Value("${topic.audit.log.name}")
    private String auditLogTopicName;

    public AuditLogProducer(KafkaProperties kafkaProperties) {
        this.kafkaTemplate = new KafkaTemplate<>(auditLogProducerFactory(kafkaProperties));
    }

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

    // ticket-service처럼 기본 Kafka value serializer로 StringSerializer를 쓰는 서비스가 있다.
    // 감사 로그는 항상 JSON으로 전송해야 하므로 감사 전용 ProducerFactory를 별도로 사용한다.
    private ProducerFactory<String, AuditLogEvent> auditLogProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.getProducer().getProperties());
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        properties.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaProducerFactory<>(properties);
    }
}
