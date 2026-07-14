package dev.bum.admin_service.feign.ticket;

import dev.bum.common.service.ticket.ticket.dto.TicketResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "ticket-service-client", url = "${services.ticket-service.url}", path = "/api/v1/ticket")
public interface TicketServiceClient {

    @GetMapping("/reservation/{reservationId}")
    List<TicketResponse> selectByReservationId(@PathVariable("reservationId") Long reservationId);
}
