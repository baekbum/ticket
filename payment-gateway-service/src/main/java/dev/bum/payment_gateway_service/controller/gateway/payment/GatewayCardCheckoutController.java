package dev.bum.payment_gateway_service.controller.gateway.payment;

import dev.bum.common.service.paymentgateway.card.*;
import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentApproveRequest;
import dev.bum.payment_gateway_service.dto.gateway.GatewayCardApprovalResponse;
import dev.bum.payment_gateway_service.security.card.GatewayCardSessionToken;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import dev.bum.payment_gateway_service.service.gateway.card.GatewayCardCheckoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class GatewayCardCheckoutController {

    private final GatewayCardCheckoutService checkout;

    // 가맹점 인증이 필요한 서버 전용 세션 발급 API.
    @PostMapping("/api/v1/payments/card/gateway/{pgPaymentId}/checkout-session")
    public GatewayCardSessionResponse issue(
            @AuthenticationPrincipal AuthenticatedMerchant merchant,
            @PathVariable String pgPaymentId,
            @Valid @RequestBody GatewayCardSessionRequest request
    ) {

        return checkout.issue(merchant, pgPaymentId, request);
    }

    // 사용자 JWT와 거래 전용 세션을 함께 검사한다. 카드 원문은 PG에서만 처리한다.
    @PostMapping("/api/v1/payments/card/checkout/{pgPaymentId}/approve")
    public GatewayCardApprovalResponse approve(
            @AuthenticationPrincipal String userId,
            @PathVariable String pgPaymentId,
            @RequestHeader(GatewayCardSessionToken.HEADER) String session,
            @Valid @RequestBody GatewayCardPaymentApproveRequest request
    ) {

        return checkout.approve(userId, pgPaymentId, session, request);
    }

    @GetMapping("/api/v1/payments/card/checkout/{pgPaymentId}")
    public GatewayCardApprovalResponse find(
            @AuthenticationPrincipal String userId,
            @PathVariable String pgPaymentId,
            @RequestHeader(GatewayCardSessionToken.HEADER) String session
    ) {

        return checkout.find(userId, pgPaymentId, session);
    }
}
