package dev.bum.payment_gateway_service.controller.gateway.payment;

import dev.bum.payment_gateway_service.dto.gateway.*;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import dev.bum.payment_gateway_service.service.gateway.virtualAccount.GatewayVirtualAccountIssuanceService;
import dev.bum.payment_gateway_service.service.gateway.virtualAccount.GatewayVirtualAccountCancellationService;
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

    private final GatewayVirtualAccountIssuanceService issuance;
    private final GatewayVirtualAccountCancellationService cancellation;

    @PostMapping("/gateway/{pgPaymentId}/issue")
    public ResponseEntity<GatewayVirtualAccountPaymentIssueResponse> issueGatewayPayment(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @PathVariable String pgPaymentId,
            @Valid @RequestBody GatewayVirtualAccountPaymentIssueRequest request
    ) {

        return ResponseEntity.ok(issuance.issue(merchant, pgPaymentId, request));
    }

    @GetMapping("/gateway/{pgPaymentId}/cancellation")
    public ResponseEntity<GatewayVirtualAccountCancellationResponse> findGatewayCancellation(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @PathVariable String pgPaymentId
    ) {

        return ResponseEntity.ok(cancellation.find(merchant, pgPaymentId));
    }

    @PostMapping("/gateway/{pgPaymentId}/cancel")
    public ResponseEntity<GatewayVirtualAccountCancellationResponse> cancelGatewayPayment(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @PathVariable String pgPaymentId,
            @Valid @RequestBody GatewayVirtualAccountPaymentCancelRequest request
    ) {

        GatewayVirtualAccountCancellationResponse response = cancellation.cancel(merchant, pgPaymentId, request);

        return ResponseEntity.status(response.completed() ? 200 : 202).body(response);
    }

}
