package dev.bum.ticket_service.controller.reservation.reservation;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationCondRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationResponse;
import dev.bum.ticket_service.service.reservation.reservation.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequestMapping("/api/v1/reservation")
@RestController
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @GetMapping("/select/id/{reservationId}")
    public ResponseEntity<ReservationResponse> selectById(
            @AuthenticationPrincipal String currentUserId,
            @PathVariable("reservationId") long id
    ) {
        return ResponseEntity.ok(reservationService.selectMyReservation(currentUserId, id));
    }

    @GetMapping("/select")
    public ResponseEntity<CustomPageResponse<ReservationResponse>> selectByCond(
            @AuthenticationPrincipal String currentUserId,
            @ModelAttribute ReservationCondRequest cond
    ) {
        return ResponseEntity.ok(reservationService.selectMyReservations(currentUserId, cond));
    }

    @PutMapping("/cancel/id/{id}")
    public ResponseEntity<Void> cancel(
            @AuthenticationPrincipal String currentUserId,
            @PathVariable("id") long id,
            @RequestBody CancelReservationRequest info
    ) {
        reservationService.cancelMyReservation(currentUserId, id, info);
        return ResponseEntity.ok().build();
    }
}
