package dev.bum.admin_service.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaDlqPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("관리자 DLQ 매핑 설정을 바인딩한다")
    void dlqMappingsBindConfiguredTopics() {
        contextRunner.withPropertyValues(
                "app.kafka-dlq.mappings[user-event.DLT]=user-event",
                "app.kafka-dlq.mappings[audit-log.DLT]=audit-log",
                "app.kafka-dlq.mappings[login-log.DLT]=login-log",
                "app.kafka-dlq.mappings[payment-completed.DLT]=payment-completed",
                "app.kafka-dlq.mappings[virtual-account-expired.DLT]=virtual-account-expired"
        ).run(context -> {
            KafkaDlqProperties properties = context.getBean(KafkaDlqProperties.class);
            assertThat(properties.getMappings()).containsExactlyInAnyOrderEntriesOf(expectedMappings());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"application.yaml", "application-prod.yaml"})
    @DisplayName("실제 로컬·운영 YAML에 로그인 로그와 가상계좌 만료를 포함한 DLQ 매핑이 등록되어 있다")
    void profileYamlIncludesManagedDltTopics(String resource) {
        contextRunnerWithYaml(resource).withPropertyValues(
                "TOPIC_USER_EVENT_NAME=user-event",
                "TOPIC_AUDIT_LOG_NAME=audit-log",
                "TOPIC_LOGIN_LOG_NAME=login-log",
                "TOPIC_PAYMENT_COMPLETED_NAME=payment-completed",
                "TOPIC_PAYMENT_VIRTUAL_ACCOUNT_EXPIRED_NAME=virtual-account-expired"
        ).run(context -> {
            KafkaDlqProperties properties = context.getBean(KafkaDlqProperties.class);
            assertThat(properties.getMappings()).containsExactlyInAnyOrderEntriesOf(expectedMappings());
            assertThat(properties.targetTopicOf("virtual-account-expired.DLT"))
                    .isEqualTo("virtual-account-expired");
        });
    }

    @ParameterizedTest
    @CsvSource({
            "TOPIC_PAYMENT_VIRTUAL_ACCOUNT_EXPIRED_NAME,virtual-account-expired,custom-account-expired",
            "TOPIC_LOGIN_LOG_NAME,login-log,custom-login-log"
    })
    @DisplayName("운영 YAML의 토픽 환경값을 DLT와 재발행 대상에 함께 반영한다")
    void productionYamlResolvesConfiguredTopicName(String propertyName, String defaultTopic, String customTopic) {
        contextRunnerWithYaml("application-prod.yaml")
                .withPropertyValues(propertyName + "=" + customTopic)
                .run(context -> {
                    KafkaDlqProperties properties = context.getBean(KafkaDlqProperties.class);
                    assertThat(properties.getMappings())
                            .containsEntry(customTopic + ".DLT", customTopic)
                            .doesNotContainKey(defaultTopic + ".DLT");
                });
    }

    @Test
    @DisplayName("DLQ entry 매핑은 topic 환경값 변경을 key와 value에 함께 반영")
    void dlqEntriesResolveTopicPlaceholdersInKeyAndValue() {
        contextRunner.withPropertyValues(
                "TOPIC_USER_EVENT_NAME=custom-user-event",
                "app.kafka-dlq.entries[0].dlt-topic=${TOPIC_USER_EVENT_NAME:user-event}.DLT",
                "app.kafka-dlq.entries[0].target-topic=${TOPIC_USER_EVENT_NAME:user-event}"
        ).run(context -> {
            KafkaDlqProperties properties = context.getBean(KafkaDlqProperties.class);
            assertThat(properties.getMappings())
                    .containsEntry("custom-user-event.DLT", "custom-user-event");
        });
    }

    private ApplicationContextRunner contextRunnerWithYaml(String resource) {
        try {
            var sources = new YamlPropertySourceLoader().load(resource, new ClassPathResource(resource));
            return contextRunner.withInitializer(context -> sources.forEach(source ->
                    context.getEnvironment().getPropertySources().addLast(source)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, String> expectedMappings() {
        return Map.of(
                "user-event.DLT", "user-event",
                "audit-log.DLT", "audit-log",
                "login-log.DLT", "login-log",
                "payment-completed.DLT", "payment-completed",
                "virtual-account-expired.DLT", "virtual-account-expired"
        );
    }

    @Configuration
    @EnableConfigurationProperties(KafkaDlqProperties.class)
    static class TestConfig {
    }
}
