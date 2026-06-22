package dev.bum.ticket_service.controller;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.seat.dto.*;

import dev.bum.ticket_service.service.seat.SeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequestMapping("/api/v1/seat")
@RestController
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @PostMapping("/insert")
    public ResponseEntity<Void> insert(@Valid @RequestBody InsertSeatRequest info) {
        seatService.insert(info);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/select/id/{seatId}")
    public ResponseEntity<SeatResponse> selectById(@PathVariable("seatId") Long id) {
        return ResponseEntity.ok(seatService.selectById(id));
    }

    @PostMapping("/select")
    public ResponseEntity<CustomPageResponse<SeatResponse>> selectByCond(@RequestBody SeatCondRequest cond) {
        return ResponseEntity.ok(seatService.selectByCond(cond));
    }

    @PutMapping("/update")
    public ResponseEntity<Void> update(@Valid @RequestBody UpdateSeatRequest info) {
        seatService.update(info);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete/id/{seatId}")
    public ResponseEntity<Void> delete(@PathVariable("seatId") Long seatId) {
        seatService.delete(seatId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Void> deleteBySeatIdList(@RequestBody DeleteSeatRequest info) {
        seatService.deleteBySeatIdList(info);
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
