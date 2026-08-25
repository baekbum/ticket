package dev.bum.payment_gateway_service.controller.virtualAccount;

import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountDepositRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountDepositResponse;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountIssueRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountIssueResponse;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountRefundRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountRefundResponse;
import dev.bum.payment_gateway_service.service.virtualAccount.GatewayVirtualAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/virtual-account")
@RequiredArgsConstructor
public class GatewayVirtualAccountController {

    private final GatewayVirtualAccountService gatewayVirtualAccountService;

    @PostMapping("/issue")
    public ResponseEntity<GatewayVirtualAccountIssueResponse> issue(
            @Valid @RequestBody GatewayVirtualAccountIssueRequest request
    ) {
        return ResponseEntity.ok(gatewayVirtualAccountService.issue(request));
    }

    @PostMapping("/deposit")
    public ResponseEntity<GatewayVirtualAccountDepositResponse> deposit(
            @Valid @RequestBody GatewayVirtualAccountDepositRequest request
    ) {
        return ResponseEntity.ok(gatewayVirtualAccountService.deposit(request));
    }

    @PostMapping("/refund")
    public ResponseEntity<GatewayVirtualAccountRefundResponse> refund(
            @Valid @RequestBody GatewayVirtualAccountRefundRequest request
    ) {
        return ResponseEntity.ok(gatewayVirtualAccountService.refund(request));
    }
}
