package dev.bum.ticket_service.controller.reservation.reservationDelivery;

import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryResponse;
import dev.bum.ticket_service.service.reservation.reservationDelivery.ReservationDeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequestMapping("/api/v1/reservation/delivery")
@RestController
@RequiredArgsConstructor
public class ReservationDeliveryController {

    private final ReservationDeliveryService reservationDeliveryService;

    @GetMapping("/select/id/{reservationDeliveryId}")
    public ResponseEntity<ReservationDeliveryResponse> selectById(
            @AuthenticationPrincipal String currentUserId,
            @PathVariable("reservationDeliveryId") long id
    ) {
        return ResponseEntity.ok(reservationDeliveryService.selectMyById(currentUserId, id));
    }

    @GetMapping("/select/reservation/{reservationId}")
    public ResponseEntity<ReservationDeliveryResponse> selectByReservationId(
            @AuthenticationPrincipal String currentUserId,
            @PathVariable("reservationId") long reservationId
    ) {
        return ResponseEntity.ok(reservationDeliveryService.selectMyByReservationId(currentUserId, reservationId));
    }

    @PutMapping("/update/id/{reservationDeliveryId}")
    public ResponseEntity<ReservationDeliveryResponse> update(
            @AuthenticationPrincipal String currentUserId,
            @PathVariable("reservationDeliveryId") long id,
            @Valid @RequestBody ReservationDeliveryRequest info
    ) {
        return ResponseEntity.ok(reservationDeliveryService.updateMyDelivery(currentUserId, id, info));
    }
}
