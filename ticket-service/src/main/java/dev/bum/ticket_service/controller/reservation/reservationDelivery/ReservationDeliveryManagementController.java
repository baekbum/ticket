package dev.bum.ticket_service.controller.reservation.reservationDelivery;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryCondRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryResponse;
import dev.bum.common.service.ticket.reservation.dto.UpdateReservationDeliveryTrackingRequest;
import dev.bum.ticket_service.service.reservation.reservationDelivery.ReservationDeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @PostMapping("/select")
    public ResponseEntity<CustomPageResponse<ReservationDeliveryResponse>> selectByCond(@RequestBody ReservationDeliveryCondRequest cond) {
        return ResponseEntity.ok(reservationDeliveryService.selectByCond(cond));
    }

    @GetMapping("/select/reservation/{reservationId}")
    public ResponseEntity<ReservationDeliveryResponse> selectByReservationId(@PathVariable("reservationId") long reservationId) {
        return ResponseEntity.ok(reservationDeliveryService.selectByReservationId(reservationId));
    }

    @PutMapping("/prepare/id/{reservationDeliveryId}")
    public ResponseEntity<ReservationDeliveryResponse> prepare(@PathVariable("reservationDeliveryId") long id) {
        return ResponseEntity.ok(reservationDeliveryService.prepare(id));
    }

    @PutMapping("/tracking/id/{reservationDeliveryId}")
    public ResponseEntity<ReservationDeliveryResponse> updateTracking(
            @PathVariable("reservationDeliveryId") long id,
            @Valid @RequestBody UpdateReservationDeliveryTrackingRequest info
    ) {
        return ResponseEntity.ok(reservationDeliveryService.updateTracking(id, info));
    }

    @PutMapping("/ship/id/{reservationDeliveryId}")
    public ResponseEntity<ReservationDeliveryResponse> ship(
            @PathVariable("reservationDeliveryId") long id,
            @Valid @RequestBody UpdateReservationDeliveryTrackingRequest info
    ) {
        return ResponseEntity.ok(reservationDeliveryService.ship(id, info));
    }

    @PutMapping("/deliver/id/{reservationDeliveryId}")
    public ResponseEntity<ReservationDeliveryResponse> deliver(@PathVariable("reservationDeliveryId") long id) {
        return ResponseEntity.ok(reservationDeliveryService.deliver(id));
    }

    @PutMapping("/return/id/{reservationDeliveryId}")
    public ResponseEntity<ReservationDeliveryResponse> returnDelivery(@PathVariable("reservationDeliveryId") long id) {
        return ResponseEntity.ok(reservationDeliveryService.returnDelivery(id));
    }

    @PutMapping("/cancel/id/{reservationDeliveryId}")
    public ResponseEntity<ReservationDeliveryResponse> cancel(@PathVariable("reservationDeliveryId") long id) {
        return ResponseEntity.ok(reservationDeliveryService.cancel(id));
    }
}
