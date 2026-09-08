package dev.bum.ticket_service.controller.checkout;

import dev.bum.common.service.ticket.checkout.dto.CheckoutFeeResponse;
import dev.bum.common.service.ticket.checkout.dto.CheckoutConfirmRequest;
import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareRequest;
import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareResponse;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.ticket_service.service.checkout.CheckoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    @Value("${app.checkout.reservation-fee-per-ticket:4000}")
    private Integer reservationFeePerTicket;

    @Value("${app.checkout.delivery-fee:3200}")
    private Integer deliveryFee;

    @GetMapping("/fees")
    public ResponseEntity<CheckoutFeeResponse> fees() {
        return ResponseEntity.ok(CheckoutFeeResponse.builder()
                .reservationFeePerTicket(reservationFeePerTicket)
                .deliveryFee(deliveryFee)
                .build());
    }

    @PostMapping("/prepare")
    public ResponseEntity<CheckoutPrepareResponse> prepare(
            @AuthenticationPrincipal String currentUserId,
            @RequestHeader(value = "X-Active-Token", required = false) String activeToken,
            @Valid @RequestBody CheckoutPrepareRequest request
    ) {
        return ResponseEntity.ok(checkoutService.prepare(currentUserId, activeToken, request));
    }

    @PostMapping("/confirm")
    public ResponseEntity<PaymentResponse> confirm(
            @AuthenticationPrincipal String currentUserId,
            @RequestHeader(value = "X-Active-Token", required = false) String activeToken,
            @Valid @RequestBody CheckoutConfirmRequest request
    ) {
        return ResponseEntity.ok(checkoutService.confirm(currentUserId, activeToken, request));
    }
}
