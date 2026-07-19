package dev.bum.admin_service.controller.reservation;

import dev.bum.admin_service.feign.reservation.ReservationServiceClient;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationCondRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationDetailResponse;
import dev.bum.common.service.ticket.reservation.dto.ReservationResponse;
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
@RequestMapping("/api/v1/reservation")
@RequiredArgsConstructor
public class AdminReservationController {

    private final ReservationServiceClient reservationServiceClient;

    @GetMapping("/select/id/{reservationId}")
    public ResponseEntity<ReservationResponse> selectById(@PathVariable("reservationId") Long reservationId) {
        return ResponseEntity.ok(reservationServiceClient.selectById(reservationId));
    }

    @GetMapping("/select/detail/{reservationId}")
    public ResponseEntity<ReservationDetailResponse> selectDetailById(@PathVariable("reservationId") Long reservationId) {
        return ResponseEntity.ok(reservationServiceClient.selectDetailById(reservationId));
    }

    @PostMapping("/select")
    public ResponseEntity<CustomPageResponse<ReservationResponse>> selectByCond(@RequestBody ReservationCondRequest cond) {
        return ResponseEntity.ok(reservationServiceClient.selectByCond(cond));
    }

    @PutMapping("/cancel/id/{reservationId}")
    public ResponseEntity<Void> cancel(
            @PathVariable("reservationId") Long reservationId,
            @Valid @RequestBody CancelReservationRequest info
    ) {
        reservationServiceClient.cancel(reservationId, info);
        return ResponseEntity.ok().build();
    }
}
