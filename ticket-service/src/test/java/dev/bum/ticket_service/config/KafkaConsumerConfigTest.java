package dev.bum.ticket_service.config;

import dev.bum.common.kafka.dlt.KafkaDltSlackNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaConsumerConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
            .withUserConfiguration(KafkaConsumerConfig.class)
            .withPropertyValues("spring.kafka.bootstrap-servers=localhost:9092")
            .withBean(KafkaDltSlackNotifier.class, () -> mock(KafkaDltSlackNotifier.class));

    @Test
    @DisplayName("Kafka DLT error handler bean을 생성")
    void kafkaErrorHandlerBeanCreated() {
        contextRunner.run(context ->
                assertThat(context).hasSingleBean(DefaultErrorHandler.class)
        );
    }
}
