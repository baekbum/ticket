package dev.bum.client_api_service.controller.ticket;

import dev.bum.client_api_service.feign.ticket.TicketSeatServiceClient;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.seat.dto.SeatResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
    public ResponseEntity<CustomPageResponse<SeatResponse>> selectByCond(
            @RequestParam("eventId") Long eventId,
            @RequestParam("areaId") Long areaId,
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        return ResponseEntity.ok(ticketSeatServiceClient.selectByCond(
                eventId,
                areaId,
                0,
                10000,
                List.of("seatRow-asc", "seatCol-asc"),
                authorizationHeader
        ));
    }
}
