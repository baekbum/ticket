package dev.bum.payment_gateway_service.controller.gateway;

import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentApproveRequest;
import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentRefundRequest;
import dev.bum.payment_gateway_service.service.gateway.card.GatewayCardApiService;
import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentApproveResponse;
import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentStatusResponse;
import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentRefundResponse;
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

    private final GatewayCardApiService cards;
    private final GatewayCardApprovalService approvals;

    @PostMapping("/approve")
    public ResponseEntity<GatewayCardPaymentApproveResponse> approve(
            @AuthenticationPrincipal String currentUserId,
            @Valid @RequestBody GatewayCardPaymentApproveRequest request
    ) {

        return ResponseEntity.ok(cards.approve(currentUserId, request));
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
    public ResponseEntity<GatewayCardApprovalResponse> findGatewayStatus(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @PathVariable String pgPaymentId
    ) {

        return ResponseEntity.ok(approvals.status(merchant, pgPaymentId));
    }

    @GetMapping("/{paymentNo}")
    public ResponseEntity<GatewayCardPaymentStatusResponse> findStatus(
            @AuthenticationPrincipal String currentUserId,
            @PathVariable String paymentNo
    ) {

        return ResponseEntity.ok(cards.status(currentUserId, paymentNo));
    }

    @GetMapping("/internal/{paymentNo}/approval-exists")
    public ResponseEntity<Boolean> findApprovalExists(@PathVariable String paymentNo) {

        return ResponseEntity.ok(cards.approvalExists(paymentNo));
    }

    @PostMapping("/refund")
    public ResponseEntity<GatewayCardPaymentRefundResponse> refund(
            @Valid @RequestBody GatewayCardPaymentRefundRequest request
    ) {

        return ResponseEntity.ok(cards.refund(request));
    }
}
