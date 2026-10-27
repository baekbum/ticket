package dev.bum.ticket_service.feign.paymentgateway;

import dev.bum.common.service.ticket.payment.dto.GatewayRefundResolutionRequest;
import dev.bum.common.service.ticket.payment.dto.GatewayRefundResolutionResponse;
import dev.bum.ticket_service.config.PaymentGatewayFeignConfig;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-gateway-refund-client", url = "${app.payment-gateway.base-url}",
        configuration = PaymentGatewayFeignConfig.class)
public interface PaymentGatewayRefundClient {
    @PostMapping("/api/v1/payments/refunds/internal/resolve")
    GatewayRefundResolutionResponse resolve(@RequestBody GatewayRefundResolutionRequest request);
}
