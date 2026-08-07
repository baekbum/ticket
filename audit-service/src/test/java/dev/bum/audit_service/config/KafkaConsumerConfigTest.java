package dev.bum.audit_service.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConsumerConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
            .withUserConfiguration(KafkaConsumerConfig.class)
            .withPropertyValues(
                    "spring.kafka.bootstrap-servers=localhost:9092",
                    "spring.kafka.consumer.group-id=audit-group"
            );

    @Test
    @DisplayName("기본 Kafka listener container factory에 DLT error handler를 적용")
    void kafkaListenerContainerFactoryHasDefaultErrorHandler() {
        contextRunner.run(context -> {
            DefaultErrorHandler errorHandler = context.getBean(DefaultErrorHandler.class);
            ConcurrentKafkaListenerContainerFactory<?, ?> factory = context.getBean(
                    "kafkaListenerContainerFactory",
                    ConcurrentKafkaListenerContainerFactory.class
            );

            ConcurrentMessageListenerContainer<?, ?> container = factory.createContainer("audit-log");

            assertThat(container.getCommonErrorHandler()).isSameAs(errorHandler);
        });
    }
}
