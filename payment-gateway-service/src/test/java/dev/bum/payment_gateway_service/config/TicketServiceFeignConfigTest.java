package dev.bum.payment_gateway_service.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TicketServiceFeignConfigTest {

    @Test
    @DisplayName("ticket-service Feign 요청에 내부 서비스 토큰 헤더를 추가한다")
    void add_internal_service_token_header() {
        TicketServiceFeignConfig config = new TicketServiceFeignConfig();
        RequestInterceptor interceptor = config.ticketServiceTokenRequestInterceptor("service-token");
        RequestTemplate requestTemplate = new RequestTemplate();

        interceptor.apply(requestTemplate);

        assertThat(requestTemplate.headers())
                .containsKey("X-Service-Token");
        assertThat(requestTemplate.headers().get("X-Service-Token"))
                .containsExactly("service-token");
    }
}
