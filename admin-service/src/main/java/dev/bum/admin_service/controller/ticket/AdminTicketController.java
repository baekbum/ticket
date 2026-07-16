package dev.bum.admin_service.controller.ticket;

import dev.bum.admin_service.feign.ticket.TicketServiceClient;
import dev.bum.common.service.ticket.ticket.dto.TicketResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/ticket")
@RequiredArgsConstructor
public class AdminTicketController {

    private final TicketServiceClient ticketServiceClient;

    @GetMapping("/reservation/{reservationId}")
    public ResponseEntity<List<TicketResponse>> selectByReservationId(@PathVariable("reservationId") Long reservationId) {
        return ResponseEntity.ok(ticketServiceClient.selectByReservationId(reservationId));
    }
}
