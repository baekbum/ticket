package dev.bum.payment_gateway_service.controller.gateway;

import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentApproveRequest;
import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentRefundRequest;
import dev.bum.payment_gateway_service.service.gateway.client.FinancialSimulationGatewayService;
import dev.bum.payment_gateway_service.service.gateway.card.GatewayCardApprovalService;
import dev.bum.payment_gateway_service.dto.gateway.GatewayCardApprovalRequest;
import dev.bum.payment_gateway_service.dto.gateway.GatewayCardApprovalResponse;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
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
    private final GatewayCardApprovalService approvals;

    @PostMapping("/approve")
    public ResponseEntity<byte[]> approve(
            @AuthenticationPrincipal String currentUserId,
            @Valid @RequestBody GatewayCardPaymentApproveRequest request
    ) {

        return financialGateway.approveCard(currentUserId, request);
    }

    @PostMapping("/gateway/{pgPaymentId}/approve")
    public ResponseEntity<GatewayCardApprovalResponse> approveGateway(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @PathVariable String pgPaymentId,
            @Valid @RequestBody GatewayCardApprovalRequest request
    ) {

        return ResponseEntity.ok(approvals.approve(merchant, pgPaymentId, request));
    }

    @GetMapping("/gateway/{pgPaymentId}")
    public ResponseEntity<GatewayCardApprovalResponse> gatewayStatus(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @PathVariable String pgPaymentId
    ) {

        return ResponseEntity.ok(approvals.status(merchant, pgPaymentId));
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
