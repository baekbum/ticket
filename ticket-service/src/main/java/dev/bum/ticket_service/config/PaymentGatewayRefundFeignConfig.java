package dev.bum.ticket_service.config;

import feign.Request;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import java.util.concurrent.TimeUnit;

public class PaymentGatewayRefundFeignConfig {

    @Bean
    public Retryer refundRetryer() {

        return Retryer.NEVER_RETRY;
    }

    @Bean
    public Request.Options refundRequestOptions() {

        return new Request.Options(2, TimeUnit.SECONDS, 80, TimeUnit.SECONDS, true);
    }
}
