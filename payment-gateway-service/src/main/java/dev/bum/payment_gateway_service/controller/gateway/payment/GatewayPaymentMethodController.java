package dev.bum.payment_gateway_service.controller.gateway.payment;

import dev.bum.common.service.paymentgateway.card.*;
import dev.bum.common.service.paymentgateway.payment.*;
import dev.bum.payment_gateway_service.service.gateway.payment.GatewayBankMethodChangeService;
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

    private final GatewayBankMethodChangeService bankChanges;

    @PostMapping("/card")
    public GatewayBankMethodChangeResponse changeToCard(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @PathVariable String pgPaymentId,
            @Valid @RequestBody GatewayBankMethodChangeRequest request
    ) {

        return bankChanges.change(merchant, pgPaymentId, request);
    }

    @PostMapping("/bank-transfer")
    public GatewayCardMethodChangeResponse changeToBank(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @PathVariable String pgPaymentId,
            @Valid @RequestBody GatewayCardMethodChangeRequest request
    ) {

        return changes.change(merchant, pgPaymentId, request);
    }
}
