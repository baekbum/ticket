package dev.bum.client_api_service.feign.ticket;

import dev.bum.common.service.ticket.event.event.dto.EventResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "client-ticket-event-service", url = "${services.ticket-service.url}", path = "/api/v1/event")
public interface TicketEventServiceClient {

    @GetMapping("/on-sale/soonest")
    List<EventResponse> selectSoonestOnSale();
}
