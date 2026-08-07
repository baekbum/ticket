package dev.bum.auth_service.config;

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

class KafkaConsumerConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
            .withUserConfiguration(KafkaConsumerConfig.class, KafkaTopicConfig.class)
            .withPropertyValues(
                    "spring.kafka.bootstrap-servers=localhost:9092",
                    "spring.kafka.consumer.group-id=auth-group",
                    "topic.user.name=user-event"
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

            ConcurrentMessageListenerContainer<?, ?> container = factory.createContainer("user-event");

            assertThat(container.getCommonErrorHandler()).isSameAs(errorHandler);
        });
    }

    @Test
    @DisplayName("사용자 이벤트 DLT topic을 설정값 기준으로 생성")
    void userEventDltTopicUsesConfiguredTopicName() {
        contextRunner.run(context -> {
            NewTopic topic = context.getBean("userEventDltTopic", NewTopic.class);

            assertThat(topic.name()).isEqualTo("user-event.DLT");
            assertThat(topic.numPartitions()).isEqualTo(1);
            assertThat(topic.replicationFactor()).isEqualTo((short) 1);
            assertThat(topic.configs())
                    .containsEntry(TopicConfig.RETENTION_MS_CONFIG, "1209600000")
                    .containsEntry(TopicConfig.RETENTION_BYTES_CONFIG, "1073741824");
        });
    }
}
