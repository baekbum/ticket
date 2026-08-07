package dev.bum.user_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTopicConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(KafkaTopicConfig.class)
            .withPropertyValues("topic.user.name=user-event");

    @Test
    @DisplayName("사용자 이벤트 원본 topic을 설정값 기준으로 생성")
    void userEventTopicUsesConfiguredTopicName() {
        contextRunner.run(context -> {
            NewTopic topic = context.getBean("userTopic", NewTopic.class);

            assertThat(topic.name()).isEqualTo("user-event");
            assertThat(topic.numPartitions()).isEqualTo(1);
            assertThat(topic.replicationFactor()).isEqualTo((short) 1);
        });
    }
}
