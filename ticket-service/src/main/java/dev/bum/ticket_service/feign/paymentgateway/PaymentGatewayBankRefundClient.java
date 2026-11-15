package dev.bum.ticket_service.feign.paymentgateway;

import dev.bum.common.service.paymentgateway.refund.*;
import dev.bum.ticket_service.config.PaymentGatewayMerchantFeignConfig;
import dev.bum.ticket_service.config.PaymentGatewayRefundFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "payment-gateway-bank-refund-client", url = "${app.payment-gateway.base-url}",
        configuration = {PaymentGatewayMerchantFeignConfig.class, PaymentGatewayRefundFeignConfig.class})
public interface PaymentGatewayBankRefundClient {

    @PostMapping("/api/v1/payments/gateway/{pgPaymentId}/refunds/{refundRequestId}")
    BankRefundResult refund(@PathVariable String pgPaymentId, @PathVariable String refundRequestId,
                           @RequestBody GatewayBankRefundRequest request);

    @PostMapping("/api/v1/payments/gateway/{pgPaymentId}/refunds/{refundRequestId}/withdraw")
    BankRefundResult withdraw(@PathVariable String pgPaymentId, @PathVariable String refundRequestId,
                             @RequestBody GatewayBankRefundRequest request);
}
