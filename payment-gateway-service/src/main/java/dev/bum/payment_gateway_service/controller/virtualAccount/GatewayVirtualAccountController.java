package dev.bum.payment_gateway_service.controller.virtualAccount;

import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountDepositRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountIssueRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountRefundRequest;
import dev.bum.common.service.ticket.payment.dto.GatewayVirtualAccountCancelRequest;
import dev.bum.payment_gateway_service.service.gateway.FinancialSimulationGatewayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/issue")
    public ResponseEntity<byte[]> issue(
            @Valid @RequestBody GatewayVirtualAccountIssueRequest request
    ) {

        return financialGateway.issueAccount(request);
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
