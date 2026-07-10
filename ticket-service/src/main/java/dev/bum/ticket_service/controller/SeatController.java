package dev.bum.ticket_service.controller;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.seat.dto.*;
import dev.bum.common.service.ticket.seat.enums.SeatCacheWarmUpMode;
import dev.bum.ticket_service.service.seat.SeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @DeleteMapping("/delete/bulk")
    public ResponseEntity<Void> deleteBulk(@Valid @RequestBody DeleteSeatRequest info) {
        seatService.deleteBySeatIdList(info);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete/area/{areaId}")
    public ResponseEntity<Void> deleteByAreaId(@PathVariable("areaId") Long areaId) {
        seatService.deleteByAreaId(areaId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/cache/warm-up/event/{eventId}")
    public ResponseEntity<String> warmUpEventSeats(
            @PathVariable("eventId") Long eventId,
            @RequestParam(value = "mode", defaultValue = "MISSING_ONLY") SeatCacheWarmUpMode mode
    ) {
        return ResponseEntity.ok(seatService.warmUpEventSeatsToCache(eventId, mode));
    }

    @PostMapping("/cache/warm-up/area/{areaId}")
    public ResponseEntity<String> warmUpAreaSeats(
            @PathVariable("areaId") Long areaId,
            @RequestParam(value = "mode", defaultValue = "MISSING_ONLY") SeatCacheWarmUpMode mode
    ) {
        return ResponseEntity.ok(seatService.warmUpAreaSeatsToCache(areaId, mode));
    }

    @DeleteMapping("/cache/event/{eventId}")
    public ResponseEntity<String> deleteEventSeatCache(@PathVariable("eventId") Long eventId) {
        return ResponseEntity.ok(seatService.deleteEventSeatsFromCache(eventId));
    }

    @DeleteMapping("/cache/area/{areaId}")
    public ResponseEntity<String> deleteAreaSeatCache(@PathVariable("areaId") Long areaId) {
        return ResponseEntity.ok(seatService.deleteAreaSeatsFromCache(areaId));
    }

    @PostMapping("/cache/seat/{seatId}/test-lock")
    public ResponseEntity<String> lockSeatCacheForCurrentUser(
            @PathVariable("seatId") Long seatId,
            @AuthenticationPrincipal String currentUserId
    ) {
        return ResponseEntity.ok(seatService.lockSeatCacheForUser(seatId, currentUserId));
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
