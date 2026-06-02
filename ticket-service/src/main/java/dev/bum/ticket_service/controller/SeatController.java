package dev.bum.ticket_service.controller;

import dev.bum.ticket_service.dto.SeatDto;
import dev.bum.ticket_service.service.seat.SeatService;
import dev.bum.ticket_service.vo.seat.InsertSeatInfo;
import dev.bum.ticket_service.vo.seat.SeatCond;
import dev.bum.ticket_service.vo.seat.SeatOccupyRequest;
import dev.bum.ticket_service.vo.seat.UpdateSeatInfo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequestMapping("/api/v1/seat")
@RestController
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @PostMapping("/insert")
    public ResponseEntity<Void> insert(@Valid @RequestBody InsertSeatInfo info) {
        seatService.insert(info);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/select/id/{seatId}")
    public ResponseEntity<SeatDto> selectById(@PathVariable("seatId") Long id) {
        return ResponseEntity.ok(seatService.selectById(id));
    }

    @PostMapping("/select")
    public ResponseEntity<PagedModel<SeatDto>> selectByCond(@RequestBody SeatCond cond) {
        return ResponseEntity.ok(new PagedModel<>(seatService.selectByCond(cond)));
    }

    @PutMapping("/update")
    public ResponseEntity<Void> update(@Valid @RequestBody UpdateSeatInfo info) {
        seatService.update(info);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete/id/{seatId}")
    public ResponseEntity<Void> delete(@PathVariable("seatId") Long seatId) {
        seatService.delete(seatId);
        return ResponseEntity.ok().build();
    }

    /**
     * 특정 공연의 좌석 데이터를 Redis에 예열(Warm-up)하는 관리자 API
     */
    @PostMapping("/warm-up/{eventId}")
    public ResponseEntity<String> warmUpSeats(@PathVariable("eventId") Long eventId) {
        seatService.warmUpSeatsToCache(eventId);
        return ResponseEntity.ok("공연 ID " + eventId + "번의 좌석 데이터 예열이 완료되었습니다.");
    }

    /**
     * 유저의 좌석 선점(임시 락) 요청 API
     */
    @PostMapping("/occupy")
    public ResponseEntity<Void> occupySeat(@RequestBody SeatOccupyRequest request) {
        seatService.occupySeat(request);

        return ResponseEntity.ok().build();
    }
}
