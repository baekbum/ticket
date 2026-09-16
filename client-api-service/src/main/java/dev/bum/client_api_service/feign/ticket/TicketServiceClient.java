package dev.bum.client_api_service.feign.ticket;

import dev.bum.common.service.ticket.ticket.dto.TicketResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "client-ticket-service", url = "${services.ticket-service.url}", path = "/api/v1/ticket")
public interface TicketServiceClient {

    @GetMapping("/reservation/{reservationId}")
    List<TicketResponse> selectByReservationId(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("reservationId") long reservationId
    );
}
