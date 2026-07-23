package dev.bum.ticket_service.controller.seat;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.seat.dto.DeleteSeatRequest;
import dev.bum.common.service.ticket.seat.dto.InsertSeatRequest;
import dev.bum.common.service.ticket.seat.dto.SeatCondRequest;
import dev.bum.common.service.ticket.seat.dto.SeatOccupyRequest;
import dev.bum.common.service.ticket.seat.dto.SeatOccupyResponse;
import dev.bum.common.service.ticket.seat.dto.SeatRedisInspectResponse;
import dev.bum.common.service.ticket.seat.dto.SeatResponse;
import dev.bum.common.service.ticket.seat.dto.UpdateSeatRequest;
import dev.bum.common.service.ticket.seat.enums.SeatCacheWarmUpMode;
import dev.bum.ticket_service.service.seat.SeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequestMapping("/api/v1/manage/seat")
@RestController
@RequiredArgsConstructor
public class SeatManagementController {

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

    @GetMapping("/cache/inspect/event/{eventId}")
    public ResponseEntity<SeatRedisInspectResponse> inspectEventSeatCache(
            @PathVariable("eventId") Long eventId,
            @RequestParam(value = "limit", defaultValue = "100") int limit
    ) {
        return ResponseEntity.ok(seatService.inspectEventSeatCache(eventId, limit));
    }

    @GetMapping("/cache/inspect/area/{areaId}")
    public ResponseEntity<SeatRedisInspectResponse> inspectAreaSeatCache(
            @PathVariable("areaId") Long areaId,
            @RequestParam(value = "limit", defaultValue = "100") int limit
    ) {
        return ResponseEntity.ok(seatService.inspectAreaSeatCache(areaId, limit));
    }

    @GetMapping("/cache/inspect/seat/{seatId}")
    public ResponseEntity<SeatRedisInspectResponse> inspectSeatCache(@PathVariable("seatId") Long seatId) {
        return ResponseEntity.ok(seatService.inspectSeatCache(seatId));
    }

    @PostMapping("/cache/seat/{seatId}/test-lock")
    public ResponseEntity<String> lockSeatCacheForCurrentUser(
            @PathVariable("seatId") Long seatId,
            @AuthenticationPrincipal String currentUserId
    ) {
        return ResponseEntity.ok(seatService.lockSeatCacheForUser(seatId, currentUserId));
    }

    @PostMapping("/cache/seat/{seatId}/test-unlock")
    public ResponseEntity<String> unlockSeatCache(@PathVariable("seatId") Long seatId) {
        return ResponseEntity.ok(seatService.unlockSeatCache(seatId));
    }

    @PostMapping("/occupy")
    public ResponseEntity<SeatOccupyResponse> occupySeat(@RequestBody SeatOccupyRequest request) {
        return ResponseEntity.ok(seatService.occupySeat(request));
    }
}
