package dev.bum.payment_gateway_service.controller.card;

import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentApproveRequest;
import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentApproveResponse;
import dev.bum.payment_gateway_service.service.card.GatewayCardPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/card")
@RequiredArgsConstructor
public class GatewayCardPaymentController {

    private final GatewayCardPaymentService gatewayCardPaymentService;

    @PostMapping("/approve")
    public ResponseEntity<GatewayCardPaymentApproveResponse> approve(
            @AuthenticationPrincipal String currentUserId,
            @Valid @RequestBody GatewayCardPaymentApproveRequest request
    ) {
        return ResponseEntity.ok(gatewayCardPaymentService.approve(currentUserId, request));
    }
}
