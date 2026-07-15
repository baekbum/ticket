package dev.bum.ticket_service.controller.ticket;

import dev.bum.common.service.ticket.ticket.dto.TicketResponse;
import dev.bum.ticket_service.service.ticket.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RequestMapping("/api/v1/ticket")
@RestController
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping("/reservation/{reservationId}")
    public ResponseEntity<List<TicketResponse>> selectByReservationId(@PathVariable("reservationId") long reservationId) {
        return ResponseEntity.ok(ticketService.selectByReservationId(reservationId));
    }
}
