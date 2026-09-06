package dev.bum.client_api_service.feign.ticket;

import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareRequest;
import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "client-ticket-checkout-service", url = "${services.ticket-service.url}", path = "/api/v1/checkout")
public interface TicketCheckoutServiceClient {

    @PostMapping("/prepare")
    CheckoutPrepareResponse prepare(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader(value = "X-Active-Token", required = false) String activeToken,
            @RequestBody CheckoutPrepareRequest request
    );
}
