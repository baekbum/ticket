package dev.bum.client_api_service.feign.ticket;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.area.dto.AreaResponse;
import dev.bum.common.service.ticket.event.eventLayout.dto.EventLayoutResponse;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "client-ticket-area-service", url = "${services.ticket-service.url}", path = "/api/v1/area")
public interface TicketAreaServiceClient {

    @GetMapping("/layout/event/{eventId}")
    EventLayoutResponse selectLayout(
            @PathVariable("eventId") Long eventId,
            @RequestHeader("Authorization") String authorizationHeader
    );

    @GetMapping("/select")
    CustomPageResponse<AreaResponse> selectByCond(
            @RequestParam("eventId") Long eventId,
            @RequestParam("page") int page,
            @RequestParam("size") int size,
            @RequestParam("sort") List<String> sort,
            @RequestHeader("Authorization") String authorizationHeader
    );
}
