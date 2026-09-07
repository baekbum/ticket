package dev.bum.client_api_service.controller.ticket;

import dev.bum.client_api_service.controller.ClientFeignErrorResponse;
import dev.bum.client_api_service.feign.ticket.TicketCheckoutServiceClient;
import org.springframework.web.bind.annotation.GetMapping;
import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareRequest;
import feign.FeignException;
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

    @GetMapping("/fees")
    public ResponseEntity<?> fees(@RequestHeader("Authorization") String authorizationHeader) {
        try {
            return ResponseEntity.ok(ticketCheckoutServiceClient.fees(authorizationHeader));
        } catch (FeignException e) {
            return ClientFeignErrorResponse.from(e);
        }
    }

    @PostMapping("/prepare")
    public ResponseEntity<?> prepare(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader(value = "X-Active-Token", required = false) String activeToken,
            @RequestBody CheckoutPrepareRequest request
    ) {
        try {
            return ResponseEntity.ok(ticketCheckoutServiceClient.prepare(authorizationHeader, activeToken, request));
        } catch (FeignException e) {
            return ClientFeignErrorResponse.from(e);
        }
    }
}
