package dev.bum.payment_gateway_service.controller.financial;

import dev.bum.common.service.paymentgateway.refund.BankRefundCommand;
import dev.bum.common.service.paymentgateway.refund.BankRefundResult;
import dev.bum.common.service.ticket.payment.dto.*;
import dev.bum.payment_gateway_service.dto.virtualAccount.*;
import dev.bum.payment_gateway_service.dto.bank.*;
import dev.bum.payment_gateway_service.dto.financial.*;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.BankVirtualAccountIssuanceService;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.BankVirtualAccountIssueCommandService;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.GatewayVirtualAccountService;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.VirtualAccountCancellationService;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.BankVirtualAccountDepositService;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.BankVirtualAccountCancellationService;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.BankVirtualAccountRefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class BankController {

    // PG가 금융 전용 인증으로 호출하는 은행 업무 경로. 기존 HTTP 계약을 유지한다.
    private static final String INTERNAL_PATH = "/internal/financial-simulations/banks/virtual-accounts";
    // 이체망/입금 제공자의 은행 입금 진입점. Postman·Bruno도 같은 기능을 호출한다.
    private static final String BANK_PATH = "/api/v1/banks/virtual-accounts";
    private final GatewayVirtualAccountService accounts;
    private final VirtualAccountCancellationService cancellation;
    private final BankVirtualAccountIssuanceService issuance;
    private final BankVirtualAccountIssueCommandService issuedAccounts;
    private final BankVirtualAccountDepositService deposits;
    private final BankVirtualAccountCancellationService pgCancellation;

    // 은행 금융 사실을 동기적으로 확정하는 환불 서비스.
    private final BankVirtualAccountRefundService refunds;

    @PostMapping(INTERNAL_PATH + "/gateway/refunds/execute")
    public BankRefundResult refundGateway(
            @Valid @RequestBody BankRefundCommand request
    ) {

        return refunds.refund(request);
    }

    @PostMapping(INTERNAL_PATH + "/gateway/refunds/withdraw")
    public BankRefundResult withdrawGatewayRefund(
            @Valid @RequestBody BankRefundCommand request
    ) {

        return refunds.withdraw(request);
    }

    @PostMapping(INTERNAL_PATH + "/issue")
    public ResponseEntity<FinancialVirtualAccountIssueResponse> issue(
            @Valid @RequestBody GatewayVirtualAccountIssueRequest request
    ) {

        return ResponseEntity.ok(FinancialVirtualAccountIssueResponse.from(accounts.issue(request)));
    }

    @PostMapping(INTERNAL_PATH + "/gateway/issue")
    public ResponseEntity<BankVirtualAccountIssueResponse> issueGatewayAccount(
            @Valid @RequestBody BankVirtualAccountIssueRequest request
    ) {

        return ResponseEntity.ok(issuance.issue(request));
    }

    @GetMapping(INTERNAL_PATH + "/gateway/{pgPaymentId}")
    public ResponseEntity<BankVirtualAccountIssueResponse> findGatewayAccount(@PathVariable String pgPaymentId) {

        return ResponseEntity.of(issuedAccounts.find(pgPaymentId));
    }

    @GetMapping(INTERNAL_PATH + "/gateway/{pgPaymentId}/deposit")
    public ResponseEntity<BankVirtualAccountDepositResponse> findGatewayDeposit(@PathVariable String pgPaymentId) {

        return ResponseEntity.of(deposits.find(pgPaymentId));
    }

    @GetMapping(INTERNAL_PATH + "/gateway/{pgPaymentId}/cancellation")
    public ResponseEntity<BankVirtualAccountCancelResponse> findGatewayCancellation(@PathVariable String pgPaymentId) {

        return ResponseEntity.of(pgCancellation.find(pgPaymentId));
    }

    @GetMapping(INTERNAL_PATH + "/{paymentNo}")
    public ResponseEntity<FinancialVirtualAccountStatusResponse> findStatus(@PathVariable String paymentNo) {

        return ResponseEntity.ok(FinancialVirtualAccountStatusResponse.from(accounts.status(paymentNo)));
    }

    // 은행 입금을 확정한다. PG 반영과 Ticksy 통보는 커밋된 은행 Outbox에서 이어진다.
    @PostMapping(BANK_PATH + "/deposit")
    public ResponseEntity<BankVirtualAccountDepositResponse> deposit(
            @Valid @RequestBody GatewayVirtualAccountDepositRequest request
    ) {

        return ResponseEntity.ok(deposits.deposit(request));
    }

    // 이전 API로 발급한 PG 미연결 계좌만 처리하는 호환 진입점.
    @PostMapping(BANK_PATH + "/legacy/deposit")
    public ResponseEntity<FinancialVirtualAccountDepositResponse> depositLegacyAccount(
            @Valid @RequestBody GatewayVirtualAccountDepositRequest request
    ) {

        return ResponseEntity.ok(FinancialVirtualAccountDepositResponse.from(accounts.deposit(request)));
    }

    @PostMapping(INTERNAL_PATH + "/gateway/{pgPaymentId}/reject-deposit")
    public ResponseEntity<BankVirtualAccountDepositResponse> rejectGatewayDeposit(@PathVariable String pgPaymentId) {

        return ResponseEntity.ok(deposits.rejectDeposit(pgPaymentId));
    }

    @PostMapping(INTERNAL_PATH + "/refund")
    public ResponseEntity<FinancialVirtualAccountRefundResponse> refund(
            @Valid @RequestBody GatewayVirtualAccountRefundRequest request
    ) {

        return ResponseEntity.ok(FinancialVirtualAccountRefundResponse.from(accounts.refund(request)));
    }

    @PostMapping(INTERNAL_PATH + "/gateway/cancel")
    public ResponseEntity<BankVirtualAccountCancellationResult> cancelGatewayAccount(
            @Valid @RequestBody BankVirtualAccountCancelRequest request
    ) {

        return ResponseEntity.ok(pgCancellation.cancelCommand(request));
    }

    @PostMapping(INTERNAL_PATH + "/cancel")
    public ResponseEntity<FinancialVirtualAccountCancelResponse> cancel(
            @Valid @RequestBody GatewayVirtualAccountCancelRequest request
    ) {

        return ResponseEntity.ok(FinancialVirtualAccountCancelResponse.from(cancellation.cancel(request)));
    }
}
