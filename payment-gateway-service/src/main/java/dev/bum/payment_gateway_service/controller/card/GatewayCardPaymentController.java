package dev.bum.payment_gateway_service.controller.card;

import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentApproveRequest;
import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentRefundRequest;
import dev.bum.payment_gateway_service.service.gateway.FinancialSimulationGatewayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/v1/payments/card")
@RequiredArgsConstructor
public class GatewayCardPaymentController {

    private final FinancialSimulationGatewayService financialGateway;

    @PostMapping("/approve")
    public ResponseEntity<byte[]> approve(
            @AuthenticationPrincipal String currentUserId,
            @Valid @RequestBody GatewayCardPaymentApproveRequest request
    ) {

        return financialGateway.approveCard(currentUserId, request);
    }

    @GetMapping("/{paymentNo}")
    public ResponseEntity<byte[]> status(
            @AuthenticationPrincipal String currentUserId,
            @PathVariable String paymentNo
    ) {

        return financialGateway.cardStatus(currentUserId, paymentNo);
    }

    @GetMapping("/internal/{paymentNo}/approval-exists")
    public ResponseEntity<byte[]> approvalExists(@PathVariable String paymentNo) {

        return financialGateway.approvalExists(paymentNo);
    }

    @PostMapping("/refund")
    public ResponseEntity<byte[]> refund(
            @Valid @RequestBody GatewayCardPaymentRefundRequest request
    ) {

        return financialGateway.refundCard(request);
    }
}
