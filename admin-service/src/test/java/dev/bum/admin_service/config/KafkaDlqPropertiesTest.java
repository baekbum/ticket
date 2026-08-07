package dev.bum.admin_service.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaDlqPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "app.kafka-dlq.mappings[user-event.DLT]=user-event",
                    "app.kafka-dlq.mappings[audit-log.DLT]=audit-log",
                    "app.kafka-dlq.mappings[payment-completed.DLT]=payment-completed"
            );

    @Test
    @DisplayName("관리자 DLQ 매핑은 실제 생성되는 DLT topic 목록과 일치")
    void dlqMappingsMatchCreatedDltTopics() {
        contextRunner.run(context -> {
            KafkaDlqProperties properties = context.getBean(KafkaDlqProperties.class);

            assertThat(properties.getMappings()).containsExactlyInAnyOrderEntriesOf(Map.of(
                    "user-event.DLT", "user-event",
                    "audit-log.DLT", "audit-log",
                    "payment-completed.DLT", "payment-completed"
            ));
        });
    }

    @Test
    @DisplayName("DLQ entry 매핑은 topic 환경값 변경을 key와 value에 함께 반영")
    void dlqEntriesResolveTopicPlaceholdersInKeyAndValue() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues(
                        "TOPIC_USER_EVENT_NAME=custom-user-event",
                        "app.kafka-dlq.entries[0].dlt-topic=${TOPIC_USER_EVENT_NAME:user-event}.DLT",
                        "app.kafka-dlq.entries[0].target-topic=${TOPIC_USER_EVENT_NAME:user-event}"
                )
                .run(context -> {
                    KafkaDlqProperties properties = context.getBean(KafkaDlqProperties.class);

                    assertThat(properties.getMappings())
                            .containsEntry("custom-user-event.DLT", "custom-user-event");
                });
    }

    @Configuration
    @EnableConfigurationProperties(KafkaDlqProperties.class)
    static class TestConfig {
    }
}
