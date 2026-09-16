package dev.bum.client_api_service.controller.ticket;

import dev.bum.client_api_service.controller.ClientFeignErrorResponse;
import dev.bum.client_api_service.feign.ticket.TicketServiceClient;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ticket")
@RequiredArgsConstructor
public class ClientTicketController {

    private final TicketServiceClient ticketServiceClient;

    @GetMapping("/reservation/{reservationId}")
    public ResponseEntity<?> selectByReservationId(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("reservationId") long reservationId
    ) {
        try {
            return ResponseEntity.ok(ticketServiceClient.selectByReservationId(authorizationHeader, reservationId));
        } catch (FeignException exception) {
            return ClientFeignErrorResponse.from(exception);
        }
    }
}
