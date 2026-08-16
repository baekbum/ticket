package dev.bum.auth_service.config;

import dev.bum.common.kafka.dlt.KafkaDltSlackNotifier;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaConsumerConfig {

    private static final long RETRY_INTERVAL_MS = 1_000L;
    private static final long MAX_RETRY_ATTEMPTS = 3L;
    private static final String DLT_SUFFIX = ".DLT";

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<Object, Object> kafkaTemplate,
            KafkaDltSlackNotifier kafkaDltSlackNotifier
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> {
                    TopicPartition dltTopicPartition = new TopicPartition(record.topic() + DLT_SUFFIX, record.partition());
                    kafkaDltSlackNotifier.notifyDlt(record, exception, dltTopicPartition);
                    return dltTopicPartition;
                }
        );

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRY_ATTEMPTS)
        );

        errorHandler.setRetryListeners((record, exception, deliveryAttempt) ->
                log.warn("Kafka message retry failed: [topic: {}, partition: {}, offset: {}, attempt: {}]",
                        record.topic(), record.partition(), record.offset(), deliveryAttempt, exception)
        );

        return errorHandler;
    }
}
