package dev.bum.client_api_service.controller.ticket;

import dev.bum.client_api_service.controller.ClientFeignErrorResponse;
import dev.bum.client_api_service.feign.ticket.TicketAreaServiceClient;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.area.dto.AreaResponse;
import dev.bum.common.service.ticket.event.eventLayout.dto.EventLayoutResponse;
import feign.FeignException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/area")
@RequiredArgsConstructor
public class ClientAreaController {

    private final TicketAreaServiceClient ticketAreaServiceClient;

    @GetMapping("/layout/event/{eventId}")
    public ResponseEntity<?> selectLayout(
            @PathVariable("eventId") Long eventId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader(value = "X-Active-Token", required = false) String activeToken
    ) {
        try {
            EventLayoutResponse response = ticketAreaServiceClient.selectLayout(eventId, authorizationHeader, activeToken);
            return response != null ? ResponseEntity.ok(response) : ResponseEntity.noContent().build();
        } catch (FeignException e) {
            return ClientFeignErrorResponse.from(e);
        }
    }

    @GetMapping("/select")
    public ResponseEntity<?> selectByCond(
            @RequestParam("eventId") Long eventId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader(value = "X-Active-Token", required = false) String activeToken
    ) {
        try {
            return ResponseEntity.ok(ticketAreaServiceClient.selectByCond(
                    eventId,
                    0,
                    500,
                    List.of("areaId-asc"),
                    authorizationHeader,
                    activeToken
            ));
        } catch (FeignException e) {
            return ClientFeignErrorResponse.from(e);
        }
    }
}
