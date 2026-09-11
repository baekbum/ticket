package dev.bum.ticket_service.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class PaymentGatewayFeignConfig {

    @Bean
    public RequestInterceptor paymentGatewayServiceTokenRequestInterceptor(
            @Value("${app.internal.service-token:local-internal-service-token}") String serviceToken
    ) {
        return requestTemplate -> requestTemplate.header("X-Service-Token", serviceToken);
    }
}
