package dev.bum.payment_gateway_service.controller.financial;

import dev.bum.payment_gateway_service.dto.card.*;
import dev.bum.common.service.paymentgateway.refund.*;
import dev.bum.payment_gateway_service.service.financial.card.CardPaymentRefundService;
import dev.bum.payment_gateway_service.dto.financial.*;
import dev.bum.payment_gateway_service.security.FinancialSimulationAuthenticationFilter;
import dev.bum.payment_gateway_service.service.financial.card.GatewayCardPaymentService;
import dev.bum.payment_gateway_service.service.financial.card.CardApprovalService;
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
    private final CardApprovalService pgApprovals;

    private final CardPaymentRefundService refunds;

    @PostMapping("/gateway/close-approval")
    public FinancialCardApprovalGateResponse closeGatewayApproval(
            @Valid @RequestBody FinancialCardApprovalGateRequest request
    ) {

        return pgApprovals.close(request);
    }

    @PostMapping("/approve")
    public ResponseEntity<FinancialCardPaymentApproveResponse> approve(
            @NotBlank @RequestHeader(FinancialSimulationAuthenticationFilter.USER_HEADER) String userId,
            @Valid @RequestBody GatewayCardPaymentApproveRequest request
    ) {

        return ResponseEntity.ok(FinancialCardPaymentApproveResponse.from(cards.approve(userId, request)));
    }

    @PostMapping("/gateway/approve")
    public ResponseEntity<FinancialCardApprovalResponse> approveGateway(
            @Valid @RequestBody FinancialCardApproveRequest request
    ) {

        return ResponseEntity.ok(pgApprovals.approve(request));
    }

    @GetMapping("/gateway/{pgPaymentId}")
    public ResponseEntity<FinancialCardApprovalResponse> findGatewayStatus(@PathVariable String pgPaymentId) {

        return ResponseEntity.of(pgApprovals.find(pgPaymentId));
    }

    @GetMapping("/payments/{paymentNo}")
    public ResponseEntity<FinancialCardPaymentStatusResponse> findStatus(
            @NotBlank @RequestHeader(FinancialSimulationAuthenticationFilter.USER_HEADER) String userId,
            @PathVariable String paymentNo
    ) {

        return ResponseEntity.ok(FinancialCardPaymentStatusResponse.from(cards.status(userId, paymentNo)));
    }

    @GetMapping("/payments/{paymentNo}/approval-exists")
    public ResponseEntity<Boolean> findApprovalExists(@PathVariable String paymentNo) {

        return ResponseEntity.ok(cards.hasApprovalHistory(paymentNo));
    }

    @PostMapping("/gateway/{pgPaymentId}/cancel")
    public ResponseEntity<FinancialCardApprovalResponse> cancelGateway(@PathVariable String pgPaymentId) {

        return ResponseEntity.ok(pgApprovals.cancel(pgPaymentId));
    }

    @PostMapping("/gateway/refunds/execute")
    public PaymentRefundResult refundGateway(@Valid @RequestBody CardRefundCommand request) {

        return refunds.refund(request);
    }

    @PostMapping("/gateway/refunds/withdraw")
    public PaymentRefundResult withdrawGatewayRefund(@Valid @RequestBody CardRefundCommand request) {

        return refunds.withdraw(request);
    }

    @PostMapping("/refund")
    public ResponseEntity<FinancialCardPaymentRefundResponse> refund(
            @Valid @RequestBody GatewayCardPaymentRefundRequest request
    ) {

        return ResponseEntity.ok(FinancialCardPaymentRefundResponse.from(cards.refund(request)));
    }
}
