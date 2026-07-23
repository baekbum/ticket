package dev.bum.ticket_service.controller.checkout;

import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareRequest;
import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareResponse;
import dev.bum.ticket_service.service.checkout.CheckoutService;
import dev.bum.ticket_service.service.queue.QueueAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final QueueAccessService queueAccessService;

    @PostMapping("/prepare")
    public ResponseEntity<CheckoutPrepareResponse> prepare(
            @AuthenticationPrincipal String currentUserId,
            @RequestHeader(value = "X-Queue-Token", required = false) String queueToken,
            @Valid @RequestBody CheckoutPrepareRequest request
    ) {
        queueAccessService.validate(request.getEventId(), currentUserId, queueToken);
        return ResponseEntity.ok(checkoutService.prepare(currentUserId, request));
    }
}
