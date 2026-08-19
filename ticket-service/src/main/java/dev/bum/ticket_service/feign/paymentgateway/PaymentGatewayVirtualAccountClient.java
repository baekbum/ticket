package dev.bum.ticket_service.feign.paymentgateway;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-gateway-virtual-account-client", url = "${app.payment-gateway.base-url}")
public interface PaymentGatewayVirtualAccountClient {

    @PostMapping("/api/v1/payments/virtual-account/issue")
    GatewayVirtualAccountIssueResponse issue(@RequestBody GatewayVirtualAccountIssueRequest request);
}
