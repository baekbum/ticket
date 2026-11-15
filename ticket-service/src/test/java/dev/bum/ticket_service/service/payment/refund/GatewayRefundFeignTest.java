package dev.bum.ticket_service.service.payment.refund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.bum.common.service.paymentgateway.refund.*;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.ticket_service.config.PaymentGatewayMerchantFeignConfig;
import dev.bum.ticket_service.config.PaymentGatewayRefundFeignConfig;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayBankRefundClient;
import feign.Feign;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.support.SpringMvcContract;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class GatewayRefundFeignTest {

    @Test
    void refundHttpHasMerchantCredentialsAndNeverRetriesAnUnconfirmedPgResponse() throws Exception {

        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> merchant = new AtomicReference<>();
        AtomicReference<String> credential = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/payments/gateway/pg/refunds/7", exchange -> {
            calls.incrementAndGet();
            merchant.set(exchange.getRequestHeaders().getFirst("Merchant"));
            credential.set(exchange.getRequestHeaders().getFirst("credentialKey"));
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();

        try {
            ObjectMapper json = new ObjectMapper();
            PaymentGatewayRefundFeignConfig config = new PaymentGatewayRefundFeignConfig();
            assertThat(config.refundRequestOptions().connectTimeoutMillis()).isEqualTo(2000);
            assertThat(config.refundRequestOptions().readTimeoutMillis()).isEqualTo(80000);
            PaymentGatewayBankRefundClient client = Feign.builder().contract(new SpringMvcContract())
                    .retryer(config.refundRetryer()).options(config.refundRequestOptions())
                    .requestInterceptor(new PaymentGatewayMerchantFeignConfig()
                            .paymentGatewayMerchantRequestInterceptor(1L, "ticksy_test-refund-credential"))
                    .encoder((value, type, template) -> {
                        template.header("Content-Type", "application/json");
                        try {
                            template.body(json.writeValueAsBytes(value), StandardCharsets.UTF_8);
                        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
                            throw new IllegalStateException(failure);
                        }
                    })
                    .target(PaymentGatewayBankRefundClient.class, "http://127.0.0.1:" + server.getAddress().getPort());
            GatewayBankRefundRequest request = new GatewayBankRefundRequest(1L, "owner", BigDecimal.TEN,
                    BankCompany.KB, "1234567890", "환불자");

            assertThatThrownBy(() -> client.refund("pg", "7", request)).isInstanceOf(FeignException.class);
            assertThat(calls).hasValue(1);
            assertThat(merchant.get()).isEqualTo("1");
            assertThat(credential.get()).isEqualTo("ticksy_test-refund-credential");
        } finally {
            server.stop(0);
        }
    }
}
