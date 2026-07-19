package dev.bum.ticket_service.controller.reservation.reservation;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.reservation.dto.CancelReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.InsertReservationRequest;
import dev.bum.common.service.ticket.reservation.dto.IsReservableRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationCondRequest;
import dev.bum.common.service.ticket.reservation.dto.ReservationResponse;
import dev.bum.ticket_service.service.reservation.reservation.ReservationService;
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
@RequestMapping("/api/v1/manage/reservation")
@RestController
@RequiredArgsConstructor
public class ReservationManagementController {

    private final ReservationService reservationService;

    @PostMapping("/insert")
    public ResponseEntity<Void> insert(@Valid @RequestBody InsertReservationRequest info) {
        reservationService.insert(info);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/select/id/{reservationId}")
    public ResponseEntity<ReservationResponse> selectById(@PathVariable("reservationId") long id) {
        return ResponseEntity.ok(reservationService.selectById(id));
    }

    @PostMapping("/select")
    public ResponseEntity<CustomPageResponse<ReservationResponse>> selectByCond(@RequestBody ReservationCondRequest cond) {
        return ResponseEntity.ok(reservationService.selectByCond(cond));
    }

    @PutMapping("/cancel/id/{id}")
    public ResponseEntity<Void> cancel(@PathVariable("id") long id, @RequestBody CancelReservationRequest info) {
        reservationService.cancel(id, info);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reservable")
    public ResponseEntity<Void> isReservable(@Valid @RequestBody IsReservableRequest info) {
        reservationService.isReservable(info);
        return ResponseEntity.ok().build();
    }
}
