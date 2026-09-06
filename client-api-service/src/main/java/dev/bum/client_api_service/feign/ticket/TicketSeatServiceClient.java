package dev.bum.client_api_service.feign.ticket;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.seat.dto.SeatOccupyRequest;
import dev.bum.common.service.ticket.seat.dto.SeatOccupyResponse;
import dev.bum.common.service.ticket.seat.dto.SeatResponse;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "client-ticket-seat-service", url = "${services.ticket-service.url}", path = "/api/v1/seat")
public interface TicketSeatServiceClient {

    @GetMapping("/select")
    CustomPageResponse<SeatResponse> selectByCond(
            @RequestParam("eventId") Long eventId,
            @RequestParam("areaId") Long areaId,
            @RequestParam("page") int page,
            @RequestParam("size") int size,
            @RequestParam("sort") List<String> sort,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader(value = "X-Active-Token", required = false) String activeToken
    );

    @PostMapping("/occupy")
    SeatOccupyResponse occupySeat(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader(value = "X-Active-Token", required = false) String activeToken,
            @RequestBody SeatOccupyRequest request
    );
}
