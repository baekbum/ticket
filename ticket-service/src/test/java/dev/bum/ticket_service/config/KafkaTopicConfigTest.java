package dev.bum.ticket_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTopicConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(KafkaTopicConfig.class)
            .withPropertyValues("topic.payment.completed.name=payment-completed");

    @Test
    @DisplayName("결제 완료 원본 topic과 DLT topic을 설정값 기준으로 생성")
    void paymentCompletedTopicsUseConfiguredTopicName() {
        contextRunner.run(context -> {
            NewTopic originTopic = context.getBean("paymentCompletedTopic", NewTopic.class);
            NewTopic dltTopic = context.getBean("paymentCompletedDltTopic", NewTopic.class);

            assertThat(originTopic.name()).isEqualTo("payment-completed");
            assertThat(originTopic.numPartitions()).isEqualTo(3);
            assertThat(originTopic.replicationFactor()).isEqualTo((short) 1);

            assertThat(dltTopic.name()).isEqualTo("payment-completed.DLT");
            assertThat(dltTopic.numPartitions()).isEqualTo(3);
            assertThat(dltTopic.replicationFactor()).isEqualTo((short) 1);
            assertThat(dltTopic.configs())
                    .containsEntry(TopicConfig.RETENTION_MS_CONFIG, "1209600000")
                    .containsEntry(TopicConfig.RETENTION_BYTES_CONFIG, "1073741824");
        });
    }
}
