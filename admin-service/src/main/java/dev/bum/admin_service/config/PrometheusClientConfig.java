package dev.bum.admin_service.config;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PrometheusClientConfig {

    @Bean
    public MeterRegistryCustomizer<PrometheusMeterRegistry> prometheusMeterRegistryCustomizer(
            @Value("${spring.application.name:admin-service}") String applicationName
    ) {
        return registry -> registry.config().commonTags("application", applicationName);
    }
}
