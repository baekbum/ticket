package dev.bum.ticket_service.config;

import dev.bum.common.kafka.dlt.KafkaDltSlackNotifier;
import dev.bum.common.kafka.payment.VirtualAccountExpiredEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

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
        recoverer.setFailIfSendResultIsError(true);

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
    public ConcurrentKafkaListenerContainerFactory<String, Object> eventKafkaListenerContainerFactory(
            KafkaProperties kafkaProperties, DefaultErrorHandler kafkaErrorHandler,
            @Value("${topic.payment.virtual-account.expired.name}") String virtualAccountExpiredTopic
    ) {
        // 새 이벤트의 토픽과 타입을 여기에 등록하면 리스너들이 같은 컨테이너 팩토리를 사용할 수 있다.
        Map<String, Class<?>> eventTypes = Map.of(
                virtualAccountExpiredTopic, VirtualAccountExpiredEvent.class
        );

        DefaultKafkaConsumerFactory<String, Object> consumerFactory = new DefaultKafkaConsumerFactory<>(
                kafkaProperties.buildConsumerProperties(),
                StringDeserializer::new,
                () -> new ErrorHandlingDeserializer<>(new TicketKafkaValueDeserializer(eventTypes))
        );

        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.getContainerProperties().setObservationEnabled(kafkaProperties.getListener().isObservationEnabled());

        return factory;
    }
}
