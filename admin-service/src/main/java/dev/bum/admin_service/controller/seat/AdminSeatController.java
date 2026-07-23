package dev.bum.admin_service.controller.seat;

import dev.bum.admin_service.feign.seat.SeatServiceClient;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.seat.dto.*;
import dev.bum.common.service.ticket.seat.enums.SeatCacheWarmUpMode;
import feign.FeignException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/seat")
@RequiredArgsConstructor
public class AdminSeatController {

    private final SeatServiceClient seatServiceClient;

    @PostMapping("/insert")
    public ResponseEntity<Void> insert(@Valid @RequestBody InsertSeatRequest info) {
        seatServiceClient.insert(info);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/select/id/{seatId}")
    public ResponseEntity<SeatResponse> selectById(@PathVariable("seatId") Long id) {
        return ResponseEntity.ok(seatServiceClient.selectById(id));
    }

    @PostMapping("/select")
    public ResponseEntity<CustomPageResponse<SeatResponse>> selectByCond(@RequestBody SeatCondRequest cond) {
        return ResponseEntity.ok(seatServiceClient.selectByCond(cond));
    }

    @PutMapping("/update")
    public ResponseEntity<Void> update(@Valid @RequestBody UpdateSeatRequest info) {
        seatServiceClient.update(info);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete/id/{seatId}")
    public ResponseEntity<Void> delete(@PathVariable("seatId") Long seatId) {
        seatServiceClient.delete(seatId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Void> deleteBySeatIdList(@RequestBody DeleteSeatRequest info) {
        seatServiceClient.deleteBySeatIdList(info);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete/bulk")
    public ResponseEntity<Void> deleteBulk(@Valid @RequestBody DeleteSeatRequest info) {
        seatServiceClient.deleteBulk(info);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete/area/{areaId}")
    public ResponseEntity<Void> deleteByAreaId(@PathVariable("areaId") Long areaId) {
        seatServiceClient.deleteByAreaId(areaId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/cache/warm-up/event/{eventId}")
    public ResponseEntity<String> warmUpEventSeats(
            @PathVariable("eventId") Long eventId,
            @RequestParam(value = "mode", defaultValue = "MISSING_ONLY") SeatCacheWarmUpMode mode
    ) {
        return ResponseEntity.ok(seatServiceClient.warmUpEventSeats(eventId, mode));
    }

    @PostMapping("/cache/warm-up/area/{areaId}")
    public ResponseEntity<String> warmUpAreaSeats(
            @PathVariable("areaId") Long areaId,
            @RequestParam(value = "mode", defaultValue = "MISSING_ONLY") SeatCacheWarmUpMode mode
    ) {
        return ResponseEntity.ok(seatServiceClient.warmUpAreaSeats(areaId, mode));
    }

    @DeleteMapping("/cache/event/{eventId}")
    public ResponseEntity<String> deleteEventSeatCache(@PathVariable("eventId") Long eventId) {
        return ResponseEntity.ok(seatServiceClient.deleteEventSeatCache(eventId));
    }

    @DeleteMapping("/cache/area/{areaId}")
    public ResponseEntity<String> deleteAreaSeatCache(@PathVariable("areaId") Long areaId) {
        return ResponseEntity.ok(seatServiceClient.deleteAreaSeatCache(areaId));
    }

    @GetMapping("/cache/inspect/event/{eventId}")
    public ResponseEntity<SeatRedisInspectResponse> inspectEventSeatCache(
            @PathVariable("eventId") Long eventId,
            @RequestParam(value = "zone", required = false) String zone,
            @RequestParam(value = "row", required = false) Integer row,
            @RequestParam(value = "col", required = false) Integer col,
            @RequestParam(value = "limit", defaultValue = "100") int limit
    ) {
        return ResponseEntity.ok(seatServiceClient.inspectEventSeatCache(eventId, zone, row, col, limit));
    }

    @PostMapping("/cache/seat/{seatId}/test-lock")
    public ResponseEntity<String> lockSeatCacheForCurrentUser(@PathVariable("seatId") Long seatId) {
        try {
            return ResponseEntity.ok(seatServiceClient.lockSeatCacheForCurrentUser(seatId));
        } catch (FeignException.Conflict e) {
            return ResponseEntity.status(e.status()).body(e.contentUTF8());
        }
    }

    @PostMapping("/cache/seat/{seatId}/test-unlock")
    public ResponseEntity<String> unlockSeatCache(@PathVariable("seatId") Long seatId) {
        try {
            return ResponseEntity.ok(seatServiceClient.unlockSeatCache(seatId));
        } catch (FeignException.Conflict e) {
            return ResponseEntity.status(e.status()).body(e.contentUTF8());
        }
    }

    @PostMapping("/occupy")
    public ResponseEntity<Void> occupySeat(@RequestBody SeatOccupyRequest request) {
        seatServiceClient.occupySeat(request);
        return ResponseEntity.ok().build();
    }
}
