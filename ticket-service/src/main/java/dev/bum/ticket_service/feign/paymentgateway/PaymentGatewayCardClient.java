package dev.bum.ticket_service.feign.paymentgateway;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-gateway-card-client", url = "${app.payment-gateway.base-url}")
public interface PaymentGatewayCardClient {

    @PostMapping("/api/v1/payments/card/refund")
    GatewayCardPaymentRefundResponse refund(@RequestBody GatewayCardPaymentRefundRequest request);
}
