package dev.bum.admin_service.controller.reservation;

import dev.bum.admin_service.feign.reservation.ReservationDeliveryServiceClient;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryCondRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryResponse;
import dev.bum.common.service.ticket.reservation.dto.UpdateReservationDeliveryTrackingRequest;
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
@RestController
@RequestMapping("/api/v1/reservation/delivery")
@RequiredArgsConstructor
public class AdminReservationDeliveryController {

    private final ReservationDeliveryServiceClient reservationDeliveryServiceClient;

    @PostMapping("/insert/reservation/{reservationId}")
    public ResponseEntity<ReservationDeliveryResponse> insert(
            @PathVariable("reservationId") Long reservationId,
            @Valid @RequestBody ReservationDeliveryRequest request
    ) {
        return ResponseEntity.ok(reservationDeliveryServiceClient.insert(reservationId, request));
    }

    @GetMapping("/select/id/{reservationDeliveryId}")
    public ResponseEntity<ReservationDeliveryResponse> selectById(@PathVariable("reservationDeliveryId") Long reservationDeliveryId) {
        return ResponseEntity.ok(reservationDeliveryServiceClient.selectById(reservationDeliveryId));
    }

    @PostMapping("/select")
    public ResponseEntity<CustomPageResponse<ReservationDeliveryResponse>> selectByCond(@RequestBody ReservationDeliveryCondRequest cond) {
        return ResponseEntity.ok(reservationDeliveryServiceClient.selectByCond(cond));
    }

    @GetMapping("/select/reservation/{reservationId}")
    public ResponseEntity<ReservationDeliveryResponse> selectByReservationId(@PathVariable("reservationId") Long reservationId) {
        return ResponseEntity.ok(reservationDeliveryServiceClient.selectByReservationId(reservationId));
    }

    @PutMapping("/prepare/id/{reservationDeliveryId}")
    public ResponseEntity<ReservationDeliveryResponse> prepare(@PathVariable("reservationDeliveryId") Long reservationDeliveryId) {
        return ResponseEntity.ok(reservationDeliveryServiceClient.prepare(reservationDeliveryId));
    }

    @PutMapping("/tracking/id/{reservationDeliveryId}")
    public ResponseEntity<ReservationDeliveryResponse> updateTracking(
            @PathVariable("reservationDeliveryId") Long reservationDeliveryId,
            @Valid @RequestBody UpdateReservationDeliveryTrackingRequest request
    ) {
        return ResponseEntity.ok(reservationDeliveryServiceClient.updateTracking(reservationDeliveryId, request));
    }

    @PutMapping("/ship/id/{reservationDeliveryId}")
    public ResponseEntity<ReservationDeliveryResponse> ship(
            @PathVariable("reservationDeliveryId") Long reservationDeliveryId,
            @Valid @RequestBody UpdateReservationDeliveryTrackingRequest request
    ) {
        return ResponseEntity.ok(reservationDeliveryServiceClient.ship(reservationDeliveryId, request));
    }

    @PutMapping("/deliver/id/{reservationDeliveryId}")
    public ResponseEntity<ReservationDeliveryResponse> deliver(@PathVariable("reservationDeliveryId") Long reservationDeliveryId) {
        return ResponseEntity.ok(reservationDeliveryServiceClient.deliver(reservationDeliveryId));
    }

    @PutMapping("/return/id/{reservationDeliveryId}")
    public ResponseEntity<ReservationDeliveryResponse> returnDelivery(@PathVariable("reservationDeliveryId") Long reservationDeliveryId) {
        return ResponseEntity.ok(reservationDeliveryServiceClient.returnDelivery(reservationDeliveryId));
    }

    @PutMapping("/cancel/id/{reservationDeliveryId}")
    public ResponseEntity<ReservationDeliveryResponse> cancel(@PathVariable("reservationDeliveryId") Long reservationDeliveryId) {
        return ResponseEntity.ok(reservationDeliveryServiceClient.cancel(reservationDeliveryId));
    }
}
