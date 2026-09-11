package dev.bum.ticket_service.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentGatewayFeignConfigTest {

    @Test
    void adds_internal_service_token_header() {
        RequestInterceptor interceptor = new PaymentGatewayFeignConfig()
                .paymentGatewayServiceTokenRequestInterceptor("test-service-token");
        RequestTemplate request = new RequestTemplate();

        interceptor.apply(request);

        assertThat(request.headers().get("X-Service-Token"))
                .containsExactly("test-service-token");
    }
}
