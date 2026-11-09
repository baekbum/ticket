package dev.bum.payment_gateway_service.controller.financial;

import dev.bum.common.service.ticket.payment.dto.*;
import dev.bum.payment_gateway_service.dto.virtualAccount.*;
import dev.bum.payment_gateway_service.dto.bank.*;
import dev.bum.payment_gateway_service.dto.financial.*;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.BankVirtualAccountIssuanceService;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.BankVirtualAccountIssueCommandService;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.GatewayVirtualAccountService;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.VirtualAccountCancellationService;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.BankVirtualAccountDepositService;
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
    private final BankVirtualAccountDepositService deposits;

    @PostMapping("/issue")
    public ResponseEntity<FinancialVirtualAccountIssueResponse> issue(
            @Valid @RequestBody GatewayVirtualAccountIssueRequest request
    ) {

        return ResponseEntity.ok(FinancialVirtualAccountIssueResponse.from(accounts.issue(request)));
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

    @GetMapping("/gateway/{pgPaymentId}/deposit")
    public ResponseEntity<BankVirtualAccountDepositResponse> gatewayDeposit(@PathVariable String pgPaymentId) {

        return ResponseEntity.of(deposits.find(pgPaymentId));
    }

    @GetMapping("/{paymentNo}")
    public ResponseEntity<FinancialVirtualAccountStatusResponse> status(@PathVariable String paymentNo) {

        return ResponseEntity.ok(FinancialVirtualAccountStatusResponse.from(accounts.status(paymentNo)));
    }

    @PostMapping("/gateway/deposit")
    public ResponseEntity<BankVirtualAccountDepositResponse> depositGatewayAccount(
            @Valid @RequestBody GatewayVirtualAccountDepositRequest request
    ) {

        return ResponseEntity.ok(deposits.deposit(request));
    }

    @PostMapping("/gateway/{pgPaymentId}/reject-deposit")
    public ResponseEntity<BankVirtualAccountDepositResponse> rejectGatewayDeposit(@PathVariable String pgPaymentId) {

        return ResponseEntity.ok(deposits.rejectDeposit(pgPaymentId));
    }

    @PostMapping("/deposit")
    public ResponseEntity<FinancialVirtualAccountDepositResponse> deposit(
            @Valid @RequestBody GatewayVirtualAccountDepositRequest request
    ) {

        return ResponseEntity.ok(FinancialVirtualAccountDepositResponse.from(accounts.deposit(request)));
    }

    @PostMapping("/refund")
    public ResponseEntity<FinancialVirtualAccountRefundResponse> refund(
            @Valid @RequestBody GatewayVirtualAccountRefundRequest request
    ) {

        return ResponseEntity.ok(FinancialVirtualAccountRefundResponse.from(accounts.refund(request)));
    }

    @PostMapping("/cancel")
    public ResponseEntity<FinancialVirtualAccountCancelResponse> cancel(
            @Valid @RequestBody GatewayVirtualAccountCancelRequest request
    ) {

        return ResponseEntity.ok(FinancialVirtualAccountCancelResponse.from(cancellation.cancel(request)));
    }
}
