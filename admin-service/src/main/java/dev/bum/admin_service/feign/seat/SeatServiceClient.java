package dev.bum.admin_service.feign.seat;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.seat.dto.*;
import dev.bum.common.service.ticket.seat.enums.SeatCacheWarmUpMode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "seat-service", url = "${services.ticket-service.url}", path = "/api/v1/seat")
public interface SeatServiceClient {

    @PostMapping("/insert")
    void insert(@RequestBody InsertSeatRequest info);

    @GetMapping("/select/id/{seatId}")
    SeatResponse selectById(@PathVariable("seatId") Long seatId);

    @PostMapping("/select")
    CustomPageResponse<SeatResponse> selectByCond(@RequestBody SeatCondRequest cond);

    @PutMapping("/update")
    void update(@RequestBody UpdateSeatRequest info);

    @DeleteMapping("/delete/id/{seatId}")
    void delete(@PathVariable("seatId") Long seatId);

    @DeleteMapping("/delete")
    void deleteBySeatIdList(@RequestBody DeleteSeatRequest info);

    @DeleteMapping("/delete/bulk")
    void deleteBulk(@RequestBody DeleteSeatRequest info);

    @DeleteMapping("/delete/area/{areaId}")
    void deleteByAreaId(@PathVariable("areaId") Long areaId);

    @PostMapping("/cache/warm-up/event/{eventId}")
    String warmUpEventSeats(@PathVariable("eventId") Long eventId,
                            @RequestParam("mode") SeatCacheWarmUpMode mode);

    @PostMapping("/cache/warm-up/area/{areaId}")
    String warmUpAreaSeats(@PathVariable("areaId") Long areaId,
                           @RequestParam("mode") SeatCacheWarmUpMode mode);

    @DeleteMapping("/cache/event/{eventId}")
    String deleteEventSeatCache(@PathVariable("eventId") Long eventId);

    @DeleteMapping("/cache/area/{areaId}")
    String deleteAreaSeatCache(@PathVariable("areaId") Long areaId);

    @PostMapping("/cache/seat/{seatId}/test-lock")
    String lockSeatCacheForCurrentUser(@PathVariable("seatId") Long seatId);

    @PostMapping("/occupy")
    void occupySeat(@RequestBody SeatOccupyRequest request);
}

