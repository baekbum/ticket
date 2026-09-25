package dev.bum.audit_service.config;

import dev.bum.common.kafka.login.LoginLogEvent;
import dev.bum.common.kafka.dlt.KafkaDltSlackNotifier;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

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

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, LoginLogEvent> loginLogKafkaListenerContainerFactory(
            KafkaProperties kafkaProperties,
            DefaultErrorHandler kafkaErrorHandler
    ) {
        Map<String, Object> consumerProperties = new HashMap<>(kafkaProperties.buildConsumerProperties());
        consumerProperties.remove(JsonDeserializer.VALUE_DEFAULT_TYPE);
        consumerProperties.remove(JsonDeserializer.KEY_DEFAULT_TYPE);
        consumerProperties.remove(JsonDeserializer.USE_TYPE_INFO_HEADERS);

        JsonDeserializer<LoginLogEvent> valueDeserializer = new JsonDeserializer<>(LoginLogEvent.class, false);
        DefaultKafkaConsumerFactory<String, LoginLogEvent> consumerFactory = new DefaultKafkaConsumerFactory<>(
                consumerProperties,
                new StringDeserializer(),
                valueDeserializer
        );

        ConcurrentKafkaListenerContainerFactory<String, LoginLogEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
