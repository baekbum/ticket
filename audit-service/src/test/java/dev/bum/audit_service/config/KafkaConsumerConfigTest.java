package dev.bum.audit_service.config;

import dev.bum.common.kafka.dlt.KafkaDltSlackNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaConsumerConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
            .withUserConfiguration(KafkaConsumerConfig.class, KafkaTopicConfig.class)
            .withPropertyValues(
                    "spring.kafka.bootstrap-servers=localhost:9092",
                    "spring.kafka.consumer.group-id=audit-group",
                    "topic.audit.log.name=audit-log"
            )
            .withBean(KafkaDltSlackNotifier.class, () -> mock(KafkaDltSlackNotifier.class));

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

    @Test
    @DisplayName("감사 로그 원본 topic과 DLT topic을 설정값 기준으로 생성")
    void auditLogTopicsUseConfiguredTopicName() {
        contextRunner.run(context -> {
            NewTopic originTopic = context.getBean("auditLogTopic", NewTopic.class);
            NewTopic dltTopic = context.getBean("auditLogDltTopic", NewTopic.class);

            assertThat(originTopic.name()).isEqualTo("audit-log");
            assertThat(originTopic.numPartitions()).isEqualTo(3);
            assertThat(originTopic.replicationFactor()).isEqualTo((short) 1);

            assertThat(dltTopic.name()).isEqualTo("audit-log.DLT");
            assertThat(dltTopic.numPartitions()).isEqualTo(3);
            assertThat(dltTopic.replicationFactor()).isEqualTo((short) 1);
            assertThat(dltTopic.configs())
                    .containsEntry(TopicConfig.RETENTION_MS_CONFIG, "1209600000")
                    .containsEntry(TopicConfig.RETENTION_BYTES_CONFIG, "1073741824");
        });
    }
}
