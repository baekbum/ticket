package dev.bum.payment_gateway_service.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TicketServiceFeignConfig {

    @Bean
    public RequestInterceptor ticketServiceTokenRequestInterceptor(
            @Value("${app.ticket.service-token:local-internal-service-token}") String serviceToken
    ) {
        return requestTemplate -> requestTemplate.header("X-Service-Token", serviceToken);
    }
}
