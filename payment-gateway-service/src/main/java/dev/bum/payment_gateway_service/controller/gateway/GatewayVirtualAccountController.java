package dev.bum.payment_gateway_service.controller.gateway;

import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountDepositRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountIssueRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountRefundRequest;
import dev.bum.common.service.ticket.payment.dto.GatewayVirtualAccountCancelRequest;
import dev.bum.payment_gateway_service.dto.gateway.*;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import dev.bum.payment_gateway_service.service.gateway.client.FinancialSimulationGatewayService;
import dev.bum.payment_gateway_service.service.gateway.virtualAccount.GatewayVirtualAccountIssuanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/virtual-account")
@RequiredArgsConstructor
public class GatewayVirtualAccountController {

    private final FinancialSimulationGatewayService financialGateway;
    private final GatewayVirtualAccountIssuanceService issuance;

    @PostMapping("/issue")
    public ResponseEntity<byte[]> issue(
            @Valid @RequestBody GatewayVirtualAccountIssueRequest request
    ) {

        return financialGateway.issueAccount(request);
    }

    @PostMapping("/gateway/{pgPaymentId}/issue")
    public ResponseEntity<GatewayVirtualAccountPaymentIssueResponse> issueGatewayPayment(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @PathVariable String pgPaymentId,
            @Valid @RequestBody GatewayVirtualAccountPaymentIssueRequest request
    ) {

        return ResponseEntity.ok(issuance.issue(merchant, pgPaymentId, request));
    }

    @GetMapping("/internal/{paymentNo}/status")
    public ResponseEntity<byte[]> status(@PathVariable String paymentNo) {

        return financialGateway.accountStatus(paymentNo);
    }

    @PostMapping("/deposit")
    public ResponseEntity<byte[]> deposit(
            @Valid @RequestBody GatewayVirtualAccountDepositRequest request
    ) {

        return financialGateway.deposit(request);
    }

    @PostMapping("/refund")
    public ResponseEntity<byte[]> refund(
            @Valid @RequestBody GatewayVirtualAccountRefundRequest request
    ) {

        return financialGateway.refundAccount(request);
    }

    @PostMapping("/cancel")
    public ResponseEntity<byte[]> cancel(
            @Valid @RequestBody GatewayVirtualAccountCancelRequest request
    ) {

        return financialGateway.cancelAccount(request);
    }
}
