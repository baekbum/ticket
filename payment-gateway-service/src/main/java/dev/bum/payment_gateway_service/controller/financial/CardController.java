package dev.bum.payment_gateway_service.controller.financial;

import dev.bum.payment_gateway_service.dto.card.*;
import dev.bum.common.service.paymentgateway.refund.*;
import dev.bum.payment_gateway_service.service.financial.card.CardPaymentRefundService;
import dev.bum.payment_gateway_service.dto.financial.*;
import dev.bum.payment_gateway_service.service.financial.card.CardApprovalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/financial-simulations/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardApprovalService pgApprovals;

    private final CardPaymentRefundService refunds;

    @PostMapping("/gateway/close-approval")
    public FinancialCardApprovalGateResponse closeGatewayApproval(
            @Valid @RequestBody FinancialCardApprovalGateRequest request
    ) {

        return pgApprovals.close(request);
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

}
