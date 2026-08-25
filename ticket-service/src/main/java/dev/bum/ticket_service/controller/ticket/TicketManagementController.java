package dev.bum.ticket_service.controller.ticket;

import dev.bum.common.service.ticket.ticket.dto.TicketResponse;
import dev.bum.ticket_service.service.ticket.TicketManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RequestMapping("/api/v1/manage/ticket")
@RestController
@RequiredArgsConstructor
public class TicketManagementController {

    private final TicketManagementService ticketManagementService;

    @GetMapping("/reservation/{reservationId}")
    public ResponseEntity<List<TicketResponse>> selectByReservationId(@PathVariable("reservationId") long reservationId) {
        return ResponseEntity.ok(ticketManagementService.selectByReservationId(reservationId));
    }
}
