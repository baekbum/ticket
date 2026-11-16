package dev.bum.payment_gateway_service.controller.gateway.refund;

import dev.bum.common.service.paymentgateway.refund.*;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import dev.bum.payment_gateway_service.service.gateway.refund.GatewayPaymentRefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments/gateway/{pgPaymentId}/refunds/{refundRequestId}")
public class GatewayPaymentRefundController {

    private final GatewayPaymentRefundService refunds;

    @PostMapping
    public PaymentRefundResult refund(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @PathVariable String pgPaymentId,
            @PathVariable String refundRequestId,
            @Valid @RequestBody GatewayPaymentRefundRequest request
    ) {

        return refunds.execute(merchant, pgPaymentId, refundRequestId, request, false);
    }

    @PostMapping("/withdraw")
    public PaymentRefundResult withdraw(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @PathVariable String pgPaymentId,
            @PathVariable String refundRequestId,
            @Valid @RequestBody GatewayPaymentRefundRequest request
    ) {

        return refunds.execute(merchant, pgPaymentId, refundRequestId, request, true);
    }
}
