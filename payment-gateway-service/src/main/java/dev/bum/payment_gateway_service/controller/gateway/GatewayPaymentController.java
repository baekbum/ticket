package dev.bum.payment_gateway_service.controller.gateway;

import dev.bum.payment_gateway_service.dto.gateway.*;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import dev.bum.payment_gateway_service.service.gateway.payment.GatewayPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments/gateway")
@RequiredArgsConstructor
public class GatewayPaymentController {

    private final GatewayPaymentService payments;

    @PostMapping("/prepare")
    public ResponseEntity<GatewayPaymentResponse> prepare(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @Valid @RequestBody GatewayPaymentPrepareRequest request
    ) {

        return ResponseEntity.ok(payments.prepare(merchant, request));
    }

    @GetMapping("/{pgPaymentId}")
    public ResponseEntity<GatewayPaymentResponse> find(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @PathVariable String pgPaymentId
    ) {

        return ResponseEntity.ok(payments.find(merchant, pgPaymentId));
    }

    @GetMapping("/by-payment-no/{paymentNo}")
    public ResponseEntity<GatewayPaymentResponse> findByPaymentNo(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @PathVariable String paymentNo
    ) {

        return ResponseEntity.ok(payments.findByPaymentNo(merchant, paymentNo));
    }
}
