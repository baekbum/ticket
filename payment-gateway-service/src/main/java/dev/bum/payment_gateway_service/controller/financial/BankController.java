package dev.bum.payment_gateway_service.controller.financial;

import dev.bum.common.service.ticket.payment.dto.*;
import dev.bum.payment_gateway_service.dto.virtualAccount.*;
import dev.bum.payment_gateway_service.dto.bank.*;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.BankVirtualAccountIssuanceService;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.BankVirtualAccountIssueCommandService;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.GatewayVirtualAccountService;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.VirtualAccountCancellationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/financial-simulations/banks/virtual-accounts")
@RequiredArgsConstructor
public class BankController {

    private final GatewayVirtualAccountService accounts;
    private final VirtualAccountCancellationService cancellation;
    private final BankVirtualAccountIssuanceService issuance;
    private final BankVirtualAccountIssueCommandService issuedAccounts;

    @PostMapping("/issue")
    public ResponseEntity<GatewayVirtualAccountIssueResponse> issue(
            @Valid @RequestBody GatewayVirtualAccountIssueRequest request
    ) {

        return ResponseEntity.ok(accounts.issue(request));
    }

    @PostMapping("/gateway/issue")
    public ResponseEntity<BankVirtualAccountIssueResponse> issueGatewayAccount(
            @Valid @RequestBody BankVirtualAccountIssueRequest request
    ) {

        return ResponseEntity.ok(issuance.issue(request));
    }

    @GetMapping("/gateway/{pgPaymentId}")
    public ResponseEntity<BankVirtualAccountIssueResponse> gatewayAccount(@PathVariable String pgPaymentId) {

        return ResponseEntity.of(issuedAccounts.find(pgPaymentId));
    }

    @GetMapping("/{paymentNo}")
    public ResponseEntity<GatewayVirtualAccountStatusResponse> status(@PathVariable String paymentNo) {

        return ResponseEntity.ok(accounts.status(paymentNo));
    }

    @PostMapping("/deposit")
    public ResponseEntity<GatewayVirtualAccountDepositResponse> deposit(
            @Valid @RequestBody GatewayVirtualAccountDepositRequest request
    ) {

        return ResponseEntity.ok(accounts.deposit(request));
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

        return ResponseEntity.ok(cancellation.cancel(request));
    }
}
