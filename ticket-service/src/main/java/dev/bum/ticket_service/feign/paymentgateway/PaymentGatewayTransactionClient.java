package dev.bum.ticket_service.feign.paymentgateway;

import dev.bum.common.service.paymentgateway.card.*;
import dev.bum.ticket_service.config.PaymentGatewayMerchantFeignConfig;
import dev.bum.ticket_service.config.PaymentGatewayTransactionFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "payment-gateway-transaction-client", url = "${app.payment-gateway.base-url}",
        configuration = {PaymentGatewayMerchantFeignConfig.class, PaymentGatewayTransactionFeignConfig.class})
public interface PaymentGatewayTransactionClient {

    @PostMapping("/api/v1/payments/gateway/prepare")
    GatewayPaymentPrepareResponse prepare(@RequestBody GatewayPaymentPrepareRequest request);

    @PostMapping("/api/v1/payments/gateway/{pgPaymentId}/method/bank-transfer")
    GatewayCardMethodChangeResponse changeCardToBank(
            @PathVariable("pgPaymentId") String pgPaymentId,
            @RequestBody GatewayCardMethodChangeRequest request
    );

    @PostMapping("/api/v1/payments/card/gateway/{pgPaymentId}/checkout-session")
    GatewayCardSessionResponse cardSession(
            @PathVariable("pgPaymentId") String pgPaymentId,
            @RequestBody GatewayCardSessionRequest request
    );

    @GetMapping("/api/v1/payments/gateway/{pgPaymentId}")
    GatewayPaymentPrepareResponse findByPgPaymentId(@PathVariable("pgPaymentId") String pgPaymentId);

    @GetMapping("/api/v1/payments/gateway/by-payment-no/{paymentNo}")
    GatewayPaymentPrepareResponse findByPaymentNo(@PathVariable("paymentNo") String paymentNo);

    @PostMapping("/api/v1/payments/virtual-account/gateway/{pgPaymentId}/issue")
    GatewayAccountIssueResponse issue(
            @PathVariable("pgPaymentId") String pgPaymentId,
            @RequestBody GatewayAccountIssueRequest request
    );

    @PostMapping("/api/v1/payments/virtual-account/gateway/{pgPaymentId}/cancel")
    ResponseEntity<GatewayAccountCancelResponse> cancel(
            @PathVariable("pgPaymentId") String pgPaymentId,
            @RequestBody GatewayAccountCancelRequest request
    );
}
