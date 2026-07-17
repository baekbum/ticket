package dev.bum.ticket_service.controller.reservation.delivery;

import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryResponse;
import dev.bum.ticket_service.service.reservation.delivery.ReservationDeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequestMapping("/api/v1/manage/reservation/delivery")
@RestController
@RequiredArgsConstructor
public class ReservationDeliveryManagementController {

    private final ReservationDeliveryService reservationDeliveryService;

    @PostMapping("/insert/reservation/{reservationId}")
    public ResponseEntity<ReservationDeliveryResponse> insert(
            @PathVariable("reservationId") long reservationId,
            @Valid @RequestBody ReservationDeliveryRequest info
    ) {
        return ResponseEntity.ok(reservationDeliveryService.insert(reservationId, info));
    }

    @GetMapping("/select/id/{reservationDeliveryId}")
    public ResponseEntity<ReservationDeliveryResponse> selectById(@PathVariable("reservationDeliveryId") long id) {
        return ResponseEntity.ok(reservationDeliveryService.selectById(id));
    }

    @GetMapping("/select/reservation/{reservationId}")
    public ResponseEntity<ReservationDeliveryResponse> selectByReservationId(@PathVariable("reservationId") long reservationId) {
        return ResponseEntity.ok(reservationDeliveryService.selectByReservationId(reservationId));
    }
}
