package dev.bum.payment_gateway_service.controller.gateway.payment;

import dev.bum.common.service.paymentgateway.card.*;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import dev.bum.payment_gateway_service.service.gateway.payment.GatewayPaymentMethodChangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments/gateway/{pgPaymentId}/method")
public class GatewayPaymentMethodController {

    private final GatewayPaymentMethodChangeService changes;

    @PostMapping("/bank-transfer")
    public GatewayCardMethodChangeResponse changeToBank(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @PathVariable String pgPaymentId,
            @Valid @RequestBody GatewayCardMethodChangeRequest request
    ) {

        return changes.change(merchant, pgPaymentId, request);
    }
}
