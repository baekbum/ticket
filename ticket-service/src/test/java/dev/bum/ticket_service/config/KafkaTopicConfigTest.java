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
            .withPropertyValues("topic.event-policy.name=test-event-queue-policy");

    @Test
    @DisplayName("공연 대기열 정책 토픽 이름과 압축 정책을 적용한다")
    void eventQueuePolicyTopicUsesConfiguredNameAndCompaction() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(NewTopic.class);
            NewTopic topic = context.getBean("eventQueuePolicyTopic", NewTopic.class);

            assertThat(topic.name()).isEqualTo("test-event-queue-policy");
            assertThat(topic.numPartitions()).isEqualTo(3);
            assertThat(topic.replicationFactor()).isEqualTo((short) 1);
            assertThat(topic.configs()).containsEntry(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT);
        });
    }
}
