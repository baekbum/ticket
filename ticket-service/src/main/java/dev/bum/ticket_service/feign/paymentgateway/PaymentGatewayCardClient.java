package dev.bum.ticket_service.feign.paymentgateway;

import dev.bum.ticket_service.config.PaymentGatewayFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "payment-gateway-card-client",
        url = "${app.payment-gateway.base-url}",
        configuration = PaymentGatewayFeignConfig.class
)
public interface PaymentGatewayCardClient {

    @GetMapping("/api/v1/payments/card/internal/{paymentNo}/approval-exists")
    Boolean hasApprovalHistory(@PathVariable("paymentNo") String paymentNo);

    @PostMapping("/api/v1/payments/card/refund")
    GatewayCardPaymentRefundResponse refund(@RequestBody GatewayCardPaymentRefundRequest request);
}
