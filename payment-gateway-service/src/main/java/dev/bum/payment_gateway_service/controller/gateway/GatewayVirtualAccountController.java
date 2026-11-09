package dev.bum.payment_gateway_service.controller.gateway;

import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountDepositRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountIssueRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountRefundRequest;
import dev.bum.common.service.ticket.payment.dto.GatewayVirtualAccountCancelRequest;
import dev.bum.payment_gateway_service.dto.gateway.*;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import dev.bum.payment_gateway_service.service.gateway.virtualAccount.GatewayVirtualAccountApiService;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountIssueResponse;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountRefundResponse;
import dev.bum.common.service.ticket.payment.dto.GatewayVirtualAccountStatusResponse;
import dev.bum.common.service.ticket.payment.dto.GatewayVirtualAccountCancelResponse;
import dev.bum.payment_gateway_service.service.gateway.virtualAccount.GatewayVirtualAccountIssuanceService;
import dev.bum.payment_gateway_service.service.gateway.virtualAccount.GatewayVirtualAccountDepositService;
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

    private final GatewayVirtualAccountApiService accounts;
    private final GatewayVirtualAccountIssuanceService issuance;
    private final GatewayVirtualAccountDepositService deposits;

    @PostMapping("/issue")
    public ResponseEntity<GatewayVirtualAccountIssueResponse> issue(
            @Valid @RequestBody GatewayVirtualAccountIssueRequest request
    ) {

        return ResponseEntity.ok(accounts.issue(request));
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
    public ResponseEntity<GatewayVirtualAccountStatusResponse> status(@PathVariable String paymentNo) {

        return ResponseEntity.ok(accounts.status(paymentNo));
    }

    @PostMapping("/deposit")
    public ResponseEntity<GatewayVirtualAccountPaymentDepositResponse> deposit(
            @Valid @RequestBody GatewayVirtualAccountDepositRequest request
    ) {

        return ResponseEntity.ok(deposits.deposit(request));
    }

    @PostMapping("/refund")
    public ResponseEntity<GatewayVirtualAccountRefundResponse> refund(
            @Valid @RequestBody GatewayVirtualAccountRefundRequest request
    ) {

        return ResponseEntity.ok(accounts.refund(request));
    }

    @PostMapping("/cancel")
    public ResponseEntity<GatewayVirtualAccountCancelResponse> cancel(
            @Valid @RequestBody GatewayVirtualAccountCancelRequest request
    ) {

        return ResponseEntity.ok(accounts.cancel(request));
    }
}
