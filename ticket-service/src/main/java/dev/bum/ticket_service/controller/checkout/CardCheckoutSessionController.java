package dev.bum.ticket_service.controller.checkout;

import dev.bum.common.service.paymentgateway.card.GatewayCardSessionResponse;
import dev.bum.ticket_service.service.checkout.payment.CardCheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/checkout/payments")
public class CardCheckoutSessionController {

    private final CardCheckoutService cards;

    @PostMapping("/{paymentNo}/gateway-session")
    public GatewayCardSessionResponse session(
            @AuthenticationPrincipal String userId,
            @PathVariable String paymentNo
    ) {

        return cards.session(userId, paymentNo);
    }
}
