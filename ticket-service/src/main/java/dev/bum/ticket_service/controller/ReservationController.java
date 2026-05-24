package dev.bum.ticket_service.controller;

import dev.bum.ticket_service.dto.ReservationDto;
import dev.bum.ticket_service.service.reservation.ReservationService;
import dev.bum.ticket_service.vo.reservation.CancelReservationInfo;
import dev.bum.ticket_service.vo.reservation.InsertReservationInfo;
import dev.bum.ticket_service.vo.reservation.IsReservableInfo;
import dev.bum.ticket_service.vo.reservation.ReservationCond;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequestMapping("/api/v1/reservation")
@RestController
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping("/insert")
    public ResponseEntity<Void> insert(@Valid @RequestBody InsertReservationInfo info) {
        reservationService.insert(info);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/select/id/{reservationId}")
    public ResponseEntity<ReservationDto> selectById(@PathVariable("reservationId") long id) {
        return ResponseEntity.ok(reservationService.selectById(id));
    }

    @PostMapping("/select")
    public ResponseEntity<PagedModel<ReservationDto>> selectByCond(@RequestBody ReservationCond cond) {
        return ResponseEntity.ok(new PagedModel<>(reservationService.selectByCond(cond)));
    }

    @PutMapping("/cancel/id/{id}")
    public ResponseEntity<Void> cancel(@PathVariable("id") long id, @RequestBody CancelReservationInfo info) {
        reservationService.cancel(id, info);
        return ResponseEntity.ok().build();
    }

    /**
     * 예매 버튼을 누를 때 예매가 추가적으로 가능한지 검증하는 프로세스.
     * 티켓 예매시 내부적으로 한번 더 검사함.
     * @param info
     * @return
     */
    @PutMapping("/reservable")
    public ResponseEntity<Void> isReservable(@Valid @RequestBody IsReservableInfo info) {
        reservationService.isReservable(info);
        return ResponseEntity.ok().build();
    }
}
