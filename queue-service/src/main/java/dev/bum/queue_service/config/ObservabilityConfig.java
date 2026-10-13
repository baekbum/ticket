package dev.bum.queue_service.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;

@Configuration
public class ObservabilityConfig {

    @Bean
    public ObservationPredicate actuatorServerRequestObservationPredicate() {
        return (name, context) -> {
            if (isSecurityFilterchainObservation(context.getContextualName())) {
                return false;
            }
            if (context instanceof ServerRequestObservationContext serverContext) {
                return !serverContext.getCarrier().getURI().getPath().contains("/actuator");
            }
            return true;
        };
    }

    private boolean isSecurityFilterchainObservation(String contextualName) {
        return contextualName != null && contextualName.startsWith("security filterchain");
    }
}
