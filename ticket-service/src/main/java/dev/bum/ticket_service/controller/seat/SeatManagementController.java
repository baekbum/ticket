package dev.bum.ticket_service.controller.seat;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.seat.dto.DeleteSeatRequest;
import dev.bum.common.service.ticket.seat.dto.InsertSeatGroupRequest;
import dev.bum.common.service.ticket.seat.dto.InsertSeatRequest;
import dev.bum.common.service.ticket.seat.dto.SeatCacheSyncFailureCondRequest;
import dev.bum.common.service.ticket.seat.dto.SeatCacheSyncFailureHandleResponse;
import dev.bum.common.service.ticket.seat.dto.SeatCacheSyncFailureResponse;
import dev.bum.common.service.ticket.seat.dto.SeatCondRequest;
import dev.bum.common.service.ticket.seat.dto.SeatOccupyRequest;
import dev.bum.common.service.ticket.seat.dto.SeatOccupyResponse;
import dev.bum.common.service.ticket.seat.dto.SeatRedisInspectResponse;
import dev.bum.common.service.ticket.seat.dto.SeatResponse;
import dev.bum.common.service.ticket.seat.dto.UpdateSeatRequest;
import dev.bum.common.service.ticket.seat.enums.SeatCacheWarmUpMode;
import dev.bum.common.service.ticket.seat.enums.SeatRedisInspectMode;
import dev.bum.ticket_service.service.seat.SeatCacheSyncFailureService;
import dev.bum.ticket_service.service.seat.SeatManagementService;
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

    private final SeatManagementService seatManagementService;
    private final SeatCacheSyncFailureService seatCacheSyncFailureService;

    @PostMapping("/insert")
    public ResponseEntity<Void> insert(@Valid @RequestBody InsertSeatRequest info) {
        seatManagementService.insert(info);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/insert/group")
    public ResponseEntity<Void> insertByGroup(@Valid @RequestBody InsertSeatGroupRequest info) {
        seatManagementService.insertByEventGroupCode(info);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/select/id/{seatId}")
    public ResponseEntity<SeatResponse> selectById(@PathVariable("seatId") Long id) {
        return ResponseEntity.ok(seatManagementService.selectById(id));
    }

    @PostMapping("/select")
    public ResponseEntity<CustomPageResponse<SeatResponse>> selectByCond(@RequestBody SeatCondRequest cond) {
        return ResponseEntity.ok(seatManagementService.selectByCond(cond));
    }

    @PostMapping("/test/select")
    public ResponseEntity<CustomPageResponse<SeatResponse>> selectByCondWithCacheStatus(@RequestBody SeatCondRequest cond) {
        return ResponseEntity.ok(seatManagementService.selectByCondWithCacheStatus(cond));
    }

    @PutMapping("/update")
    public ResponseEntity<Void> update(@Valid @RequestBody UpdateSeatRequest info) {
        seatManagementService.update(info);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete/id/{seatId}")
    public ResponseEntity<Void> delete(@PathVariable("seatId") Long seatId) {
        seatManagementService.delete(seatId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Void> deleteBySeatIdList(@RequestBody DeleteSeatRequest info) {
        seatManagementService.deleteBySeatIdList(info);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete/bulk")
    public ResponseEntity<Void> deleteBulk(@Valid @RequestBody DeleteSeatRequest info) {
        seatManagementService.deleteBySeatIdList(info);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete/area/{areaId}")
    public ResponseEntity<Void> deleteByAreaId(@PathVariable("areaId") Long areaId) {
        seatManagementService.deleteByAreaId(areaId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/cache/warm-up/event/{eventId}")
    public ResponseEntity<String> warmUpEventSeats(
            @PathVariable("eventId") Long eventId,
            @RequestParam(value = "mode", defaultValue = "MISSING_ONLY") SeatCacheWarmUpMode mode
    ) {
        return ResponseEntity.ok(seatManagementService.warmUpEventSeatsToCache(eventId, mode));
    }

    @PostMapping("/cache/warm-up/area/{areaId}")
    public ResponseEntity<String> warmUpAreaSeats(
            @PathVariable("areaId") Long areaId,
            @RequestParam(value = "mode", defaultValue = "MISSING_ONLY") SeatCacheWarmUpMode mode
    ) {
        return ResponseEntity.ok(seatManagementService.warmUpAreaSeatsToCache(areaId, mode));
    }

    @DeleteMapping("/cache/event/{eventId}")
    public ResponseEntity<String> deleteEventSeatCache(@PathVariable("eventId") Long eventId) {
        return ResponseEntity.ok(seatManagementService.deleteEventSeatsFromCache(eventId));
    }

    @DeleteMapping("/cache/area/{areaId}")
    public ResponseEntity<String> deleteAreaSeatCache(@PathVariable("areaId") Long areaId) {
        return ResponseEntity.ok(seatManagementService.deleteAreaSeatsFromCache(areaId));
    }

    @GetMapping("/cache/inspect/event/{eventId}")
    public ResponseEntity<SeatRedisInspectResponse> inspectEventSeatCache(
            @PathVariable("eventId") Long eventId,
            @RequestParam(value = "zone", required = false) String zone,
            @RequestParam(value = "row", required = false) Integer row,
            @RequestParam(value = "col", required = false) Integer col,
            @RequestParam(value = "limit", defaultValue = "100") int limit,
            @RequestParam(value = "mode", defaultValue = "SEAT") SeatRedisInspectMode mode
    ) {
        return ResponseEntity.ok(seatManagementService.inspectEventSeatCache(eventId, zone, row, col, limit, mode));
    }

    @PostMapping("/cache/seat/{seatId}/test-lock")
    public ResponseEntity<String> lockSeatCacheForCurrentUser(
            @PathVariable("seatId") Long seatId,
            @AuthenticationPrincipal String currentUserId
    ) {
        return ResponseEntity.ok(seatManagementService.lockSeatCacheForUser(seatId, currentUserId));
    }

    @PostMapping("/cache/seat/{seatId}/test-unlock")
    public ResponseEntity<String> unlockSeatCache(@PathVariable("seatId") Long seatId) {
        return ResponseEntity.ok(seatManagementService.unlockSeatCache(seatId));
    }

    @PostMapping("/cache/event/{eventId}/test-unlock")
    public ResponseEntity<String> unlockEventSeatCache(@PathVariable("eventId") Long eventId) {
        return ResponseEntity.ok(seatManagementService.unlockEventSeatCache(eventId));
    }

    @PostMapping("/occupy")
    public ResponseEntity<SeatOccupyResponse> occupySeat(@RequestBody SeatOccupyRequest request) {
        return ResponseEntity.ok(seatManagementService.occupySeat(request));
    }

    @PostMapping("/cache/sync-failures/select")
    public ResponseEntity<CustomPageResponse<SeatCacheSyncFailureResponse>> selectCacheSyncFailures(
            @RequestBody(required = false) SeatCacheSyncFailureCondRequest cond
    ) {
        return ResponseEntity.ok(seatCacheSyncFailureService.selectByCond(cond));
    }

    @GetMapping("/cache/sync-failures/{id}")
    public ResponseEntity<SeatCacheSyncFailureResponse> selectCacheSyncFailure(@PathVariable("id") Long id) {
        return ResponseEntity.ok(seatCacheSyncFailureService.selectById(id));
    }

    @PostMapping("/cache/sync-failures/{id}/retry")
    public ResponseEntity<SeatCacheSyncFailureHandleResponse> retryCacheSyncFailure(@PathVariable("id") Long id) {
        return ResponseEntity.ok(seatCacheSyncFailureService.retry(id));
    }

    @PostMapping("/cache/sync-failures/{id}/discard")
    public ResponseEntity<SeatCacheSyncFailureHandleResponse> discardCacheSyncFailure(@PathVariable("id") Long id) {
        return ResponseEntity.ok(seatCacheSyncFailureService.discard(id));
    }
}
