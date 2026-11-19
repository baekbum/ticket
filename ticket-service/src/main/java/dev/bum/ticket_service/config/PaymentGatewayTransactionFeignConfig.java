package dev.bum.ticket_service.config;

import feign.Retryer;
import org.springframework.context.annotation.Bean;

public class PaymentGatewayTransactionFeignConfig {

    @Bean
    public Retryer paymentGatewayTransactionRetryer() {

        return Retryer.NEVER_RETRY;
    }
}
