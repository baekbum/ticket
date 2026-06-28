package dev.bum.admin_service.controller.seat;

import dev.bum.admin_service.feign.seat.SeatServiceClient;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.seat.dto.*;
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

    @PostMapping("/warm-up/{eventId}")
    public ResponseEntity<String> warmUpSeats(@PathVariable("eventId") Long eventId) {
        return ResponseEntity.ok(seatServiceClient.warmUpSeats(eventId));
    }

    @PostMapping("/occupy")
    public ResponseEntity<Void> occupySeat(@RequestBody SeatOccupyRequest request) {
        seatServiceClient.occupySeat(request);
        return ResponseEntity.ok().build();
    }
}
