package dev.bum.ticket_service.feign.paymentgateway;

import dev.bum.common.service.paymentgateway.refund.*;
import dev.bum.ticket_service.config.PaymentGatewayMerchantFeignConfig;
import dev.bum.ticket_service.config.PaymentGatewayRefundFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "payment-gateway-refund-application-client", url = "${app.payment-gateway.base-url}",
        configuration = {PaymentGatewayMerchantFeignConfig.class, PaymentGatewayRefundFeignConfig.class})
public interface PaymentGatewayRefundApplicationClient {

    @GetMapping("/api/v1/payments/gateway/{pgPaymentId}/refunds/{refundRequestId}")
    PaymentRefundResult status(@PathVariable String pgPaymentId, @PathVariable String refundRequestId);

    @PostMapping("/api/v1/payments/gateway/{pgPaymentId}/refunds/{refundRequestId}")
    PaymentRefundResult refund(
            @PathVariable String pgPaymentId,
            @PathVariable String refundRequestId,
            @RequestBody GatewayPaymentRefundRequest request
    );

    @PostMapping("/api/v1/payments/gateway/{pgPaymentId}/refunds/{refundRequestId}/withdraw")
    PaymentRefundResult withdraw(
            @PathVariable String pgPaymentId,
            @PathVariable String refundRequestId,
            @RequestBody GatewayPaymentRefundRequest request
    );
}
