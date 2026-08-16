package dev.bum.queue_service.config;

import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

@Configuration
public class ObservabilityConfig {

    @Bean
    public ObservationPredicate actuatorServerRequestObservationPredicate() {
        return (name, context) -> {
            if (isSecurityFilterchainObservation(context.getContextualName())) {
                return false;
            }
            if (context instanceof ServerRequestObservationContext serverContext) {
                return !serverContext.getCarrier().getRequestURI().contains("/actuator");
            }
            return true;
        };
    }

    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }

    private boolean isSecurityFilterchainObservation(String contextualName) {
        return contextualName != null && contextualName.startsWith("security filterchain");
    }
}
