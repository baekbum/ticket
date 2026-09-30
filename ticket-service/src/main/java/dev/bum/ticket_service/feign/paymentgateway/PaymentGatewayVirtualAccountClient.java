package dev.bum.ticket_service.feign.paymentgateway;

import dev.bum.ticket_service.config.PaymentGatewayFeignConfig;
import dev.bum.common.service.ticket.payment.dto.GatewayVirtualAccountStatusResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "payment-gateway-virtual-account-client",
        url = "${app.payment-gateway.base-url}",
        configuration = PaymentGatewayFeignConfig.class
)
public interface PaymentGatewayVirtualAccountClient {

    @GetMapping("/api/v1/payments/virtual-account/internal/{paymentNo}/status")
    GatewayVirtualAccountStatusResponse status(@PathVariable("paymentNo") String paymentNo);

    @PostMapping("/api/v1/payments/virtual-account/issue")
    GatewayVirtualAccountIssueResponse issue(@RequestBody GatewayVirtualAccountIssueRequest request);

    @PostMapping("/api/v1/payments/virtual-account/refund")
    GatewayVirtualAccountRefundResponse refund(@RequestBody GatewayVirtualAccountRefundRequest request);
}
