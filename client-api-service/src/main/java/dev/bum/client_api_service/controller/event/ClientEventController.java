package dev.bum.client_api_service.controller.event;

import dev.bum.client_api_service.feign.ticket.TicketEventServiceClient;
import dev.bum.common.service.ticket.event.event.dto.EventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/event")
@RequiredArgsConstructor
public class ClientEventController {

    private final TicketEventServiceClient ticketEventServiceClient;

    @GetMapping("/on-sale/soonest")
    public ResponseEntity<List<EventResponse>> selectSoonestOnSale() {
        return ResponseEntity.ok(ticketEventServiceClient.selectSoonestOnSale());
    }
}
