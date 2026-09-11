package dev.bum.client_api_service.controller.ticket;

import dev.bum.client_api_service.controller.ClientFeignErrorResponse;
import dev.bum.client_api_service.feign.ticket.TicketSeatServiceClient;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.seat.dto.SeatOccupyRequest;
import dev.bum.common.service.ticket.seat.dto.SeatOccupyResponse;
import dev.bum.common.service.ticket.seat.dto.SeatResponse;
import feign.FeignException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seat")
@RequiredArgsConstructor
public class ClientSeatController {

    private final TicketSeatServiceClient ticketSeatServiceClient;

    @GetMapping("/select")
    public ResponseEntity<?> selectByCond(
            @RequestParam("eventId") Long eventId,
            @RequestParam("areaId") Long areaId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader(value = "X-Active-Token", required = false) String activeToken
    ) {
        try {
            return ResponseEntity.ok(ticketSeatServiceClient.selectByCond(
                    eventId,
                    areaId,
                    0,
                    10000,
                    List.of("seatRow-asc", "seatCol-asc"),
                    authorizationHeader,
                    activeToken
            ));
        } catch (FeignException e) {
            return ClientFeignErrorResponse.from(e);
        }
    }

    @PostMapping("/occupy")
    public ResponseEntity<?> occupySeat(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader(value = "X-Active-Token", required = false) String activeToken,
            @RequestBody SeatOccupyRequest request
    ) {
        try {
            return ResponseEntity.ok(ticketSeatServiceClient.occupySeat(authorizationHeader, activeToken, request));
        } catch (FeignException e) {
            return ClientFeignErrorResponse.from(e);
        }
    }
}
