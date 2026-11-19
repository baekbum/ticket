package dev.bum.ticket_service.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class PaymentGatewayMerchantFeignConfig {

    @Bean
    public RequestInterceptor paymentGatewayMerchantRequestInterceptor(
            @Value("${app.payment-gateway.merchant-id:1}") long merchantId,
            @Value("${app.payment-gateway.credential-key:}") String credentialKey
    ) {

        return requestTemplate -> {
            if (merchantId <= 0 || credentialKey == null || credentialKey.isBlank()
                    || credentialKey.length() > 256 || !credentialKey.equals(credentialKey.strip())) {
                throw new IllegalStateException("PG 가맹점 인증 설정이 필요합니다.");
            }

            requestTemplate.removeHeader("Merchant");
            requestTemplate.removeHeader("credentialKey");
            requestTemplate.header("Merchant", Long.toString(merchantId));
            requestTemplate.header("credentialKey", credentialKey);
        };
    }
}
