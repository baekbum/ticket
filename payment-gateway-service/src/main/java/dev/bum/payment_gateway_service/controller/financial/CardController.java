package dev.bum.payment_gateway_service.controller.financial;

import dev.bum.payment_gateway_service.dto.card.*;
import dev.bum.payment_gateway_service.security.FinancialSimulationAuthenticationFilter;
import dev.bum.payment_gateway_service.service.financial.card.GatewayCardPaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/financial-simulations/cards")
@RequiredArgsConstructor
public class CardController {

    private final GatewayCardPaymentService cards;

    @PostMapping("/approve")
    public ResponseEntity<GatewayCardPaymentApproveResponse> approve(
            @NotBlank @RequestHeader(FinancialSimulationAuthenticationFilter.USER_HEADER) String userId,
            @Valid @RequestBody GatewayCardPaymentApproveRequest request
    ) {

        return ResponseEntity.ok(cards.approve(userId, request));
    }

    @GetMapping("/payments/{paymentNo}")
    public ResponseEntity<GatewayCardPaymentStatusResponse> status(
            @NotBlank @RequestHeader(FinancialSimulationAuthenticationFilter.USER_HEADER) String userId,
            @PathVariable String paymentNo
    ) {

        return ResponseEntity.ok(cards.status(userId, paymentNo));
    }

    @GetMapping("/payments/{paymentNo}/approval-exists")
    public ResponseEntity<Boolean> approvalExists(@PathVariable String paymentNo) {

        return ResponseEntity.ok(cards.hasApprovalHistory(paymentNo));
    }

    @PostMapping("/refund")
    public ResponseEntity<GatewayCardPaymentRefundResponse> refund(
            @Valid @RequestBody GatewayCardPaymentRefundRequest request
    ) {

        return ResponseEntity.ok(cards.refund(request));
    }
}
