package dev.bum.admin_service.monitoring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class FailureMetricPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "app.monitoring.failure-metrics.thresholds.queue_token_complete_failure_count.warning-threshold=1",
                    "app.monitoring.failure-metrics.thresholds.queue_token_complete_failure_count.critical-threshold=3"
            );

    @Test
    @DisplayName("핵심 장애 지표 임계치는 설정으로 바인딩")
    void thresholds_bind_from_properties() {
        contextRunner.run(context -> {
            FailureMetricProperties properties = context.getBean(FailureMetricProperties.class);

            FailureMetricProperties.Threshold threshold = properties.thresholdOf(
                    "queue_token_complete_failure_count",
                    0,
                    0
            );

            assertThat(threshold.getWarningThreshold()).isEqualTo(1);
            assertThat(threshold.getCriticalThreshold()).isEqualTo(3);
        });
    }

    @Configuration
    @EnableConfigurationProperties(FailureMetricProperties.class)
    static class TestConfig {
    }
}
