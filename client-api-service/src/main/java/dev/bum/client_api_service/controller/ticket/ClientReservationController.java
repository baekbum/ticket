package dev.bum.client_api_service.controller.ticket;

import dev.bum.client_api_service.controller.ClientFeignErrorResponse;
import dev.bum.client_api_service.feign.ticket.TicketReservationServiceClient;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationCondRequest;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservation")
@RequiredArgsConstructor
public class ClientReservationController {

    private final TicketReservationServiceClient ticketReservationServiceClient;

    @GetMapping("/select/detail/{reservationId}")
    public ResponseEntity<?> selectDetail(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("reservationId") long reservationId
    ) {
        try {
            return ResponseEntity.ok(ticketReservationServiceClient.selectDetail(authorizationHeader, reservationId));
        } catch (FeignException exception) {
            return ClientFeignErrorResponse.from(exception);
        }
    }

    @GetMapping("/select")
    public ResponseEntity<?> selectByCond(
            @RequestHeader("Authorization") String authorizationHeader,
            @ModelAttribute ReservationCondRequest condition
    ) {
        try {
            return ResponseEntity.ok(ticketReservationServiceClient.selectByCond(authorizationHeader, condition));
        } catch (FeignException exception) {
            return ClientFeignErrorResponse.from(exception);
        }
    }

    @PutMapping("/cancel/id/{reservationId}")
    public ResponseEntity<?> cancel(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("reservationId") long reservationId,
            @RequestBody CancelReservationRequest request
    ) {
        try {
            ticketReservationServiceClient.cancel(authorizationHeader, reservationId, request);
            return ResponseEntity.ok().build();
        } catch (FeignException exception) {
            return ClientFeignErrorResponse.from(exception);
        }
    }
}
