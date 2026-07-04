package dev.bum.admin_service.feign.seat;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.seat.dto.*;
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

    // 좌석(레디스) 관련 메서드
    @PostMapping("/warm-up/{eventId}")
    String warmUpSeats(@PathVariable("eventId") Long eventId);

    @PostMapping("/occupy")
    void occupySeat(@RequestBody SeatOccupyRequest request);
}

