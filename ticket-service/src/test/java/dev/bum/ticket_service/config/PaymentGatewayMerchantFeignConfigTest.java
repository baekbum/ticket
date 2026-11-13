package dev.bum.ticket_service.config;

import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PaymentGatewayMerchantFeignConfigTest {

    @Test
    void theConfiguredMerchantAndSecretReplaceCallerProvidedHeaders() {
        RequestTemplate request = new RequestTemplate();
        request.header("Merchant", "999");
        request.header("credentialKey", "forged");
        new PaymentGatewayMerchantFeignConfig().paymentGatewayMerchantRequestInterceptor(1L, "ticksy_test-secret")
                .apply(request);
        assertThat(request.headers().get("Merchant")).containsExactly("1");
        assertThat(request.headers().get("credentialKey")).containsExactly("ticksy_test-secret");
        assertThat(request.headers()).doesNotContainKey("X-Service-Token");
    }

    @Test
    void anUnprovisionedClientCannotFallBackToTheInternalServiceToken() {
        assertThatThrownBy(() -> new PaymentGatewayMerchantFeignConfig().paymentGatewayMerchantRequestInterceptor(1L, "")
                .apply(new RequestTemplate())).isInstanceOf(IllegalStateException.class);
    }
}
