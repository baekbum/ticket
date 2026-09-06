package dev.bum.client_api_service.controller.ticket;

import dev.bum.client_api_service.feign.ticket.TicketCheckoutServiceClient;
import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareRequest;
import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkout")
@RequiredArgsConstructor
public class ClientCheckoutController {

    private final TicketCheckoutServiceClient ticketCheckoutServiceClient;

    @PostMapping("/prepare")
    public ResponseEntity<CheckoutPrepareResponse> prepare(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader(value = "X-Active-Token", required = false) String activeToken,
            @RequestBody CheckoutPrepareRequest request
    ) {
        return ResponseEntity.ok(ticketCheckoutServiceClient.prepare(authorizationHeader, activeToken, request));
    }
}
