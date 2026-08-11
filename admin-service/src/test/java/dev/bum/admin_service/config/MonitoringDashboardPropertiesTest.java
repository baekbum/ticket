package dev.bum.admin_service.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringDashboardPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "app.monitoring.grafana.dashboards[0].icon=ti ti-leaf",
                    "app.monitoring.grafana.dashboards[0].title=Spring Boot / JVM",
                    "app.monitoring.grafana.dashboards[0].subtitle=SpringBoot APM Dashboard",
                    "app.monitoring.grafana.dashboards[0].description=HTTP 요청 상태를 확인합니다.",
                    "app.monitoring.grafana.dashboards[0].url=http://localhost:3001/d/spring"
            );

    @Test
    @DisplayName("Grafana 대시보드 카드 설정은 리스트로 바인딩")
    void grafana_dashboard_cards_bind_from_properties() {
        contextRunner.run(context -> {
            MonitoringDashboardProperties properties = context.getBean(MonitoringDashboardProperties.class);

            assertThat(properties.getDashboards()).hasSize(1);
            MonitoringDashboardProperties.Dashboard dashboard = properties.getDashboards().get(0);
            assertThat(dashboard.getIcon()).isEqualTo("ti ti-leaf");
            assertThat(dashboard.getTitle()).isEqualTo("Spring Boot / JVM");
            assertThat(dashboard.getUrl()).isEqualTo("http://localhost:3001/d/spring");
        });
    }

    @Configuration
    @EnableConfigurationProperties(MonitoringDashboardProperties.class)
    static class TestConfig {
    }
}
