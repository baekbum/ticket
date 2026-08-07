package dev.bum.common.kafka.dlt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaDltSlackNotifier {

    private static final int MIN_PAYLOAD_PREVIEW_LENGTH = 100;

    private final KafkaDltSlackProperties properties;
    private final RestClient.Builder restClientBuilder;

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    public void notifyDlt(ConsumerRecord<?, ?> record, Exception exception, TopicPartition dltTopicPartition) {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getWebhookUrl())) {
            return;
        }

        try {
            restClientBuilder.build()
                    .post()
                    .uri(properties.getWebhookUrl())
                    .body(Map.of("text", messageOf(record, exception, dltTopicPartition)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException notifyException) {
            log.warn(
                    "DLT Slack notification failed. service={}, topic={}, partition={}, offset={}, dltTopic={}",
                    serviceName,
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    dltTopicPartition.topic(),
                    notifyException
            );
        }
    }

    private String messageOf(ConsumerRecord<?, ?> record, Exception exception, TopicPartition dltTopicPartition) {
        return """
                [DLT] Kafka 메시지 처리 실패
                *Service:* %s
                *Origin Topic:* %s
                *DLT Topic:* %s
                *Partition:* %d → %d
                *Offset:* %d
                *Key:* %s
                *Exception:* %s
                *Failed At:* %s
                *Payload Preview:* %s
                """.formatted(
                serviceName,
                record.topic(),
                dltTopicPartition.topic(),
                record.partition(),
                dltTopicPartition.partition(),
                record.offset(),
                preview(record.key()),
                exceptionMessage(exception),
                LocalDateTime.now(),
                preview(record.value())
        );
    }

    private String exceptionMessage(Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        String message = cause.getMessage();
        if (!StringUtils.hasText(message)) {
            return cause.getClass().getName();
        }
        return cause.getClass().getName() + ": " + message;
    }

    private String preview(Object value) {
        if (value == null) {
            return "null";
        }

        String text;
        if (value instanceof byte[] bytes) {
            text = new String(bytes, StandardCharsets.UTF_8);
        } else {
            text = String.valueOf(value);
        }

        int maxLength = Math.max(properties.getPayloadPreviewLength(), MIN_PAYLOAD_PREVIEW_LENGTH);
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
