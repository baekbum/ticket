package dev.bum.ticket_service.controller;

import dev.bum.ticket_service.dto.EventDto;
import dev.bum.ticket_service.dto.SeatDto;
import dev.bum.ticket_service.service.seat.SeatService;
import dev.bum.ticket_service.vo.event.EventCond;
import dev.bum.ticket_service.vo.event.InsertEventInfo;
import dev.bum.ticket_service.vo.event.UpdateEventInfo;
import dev.bum.ticket_service.vo.seat.InsertSeatInfo;
import dev.bum.ticket_service.vo.seat.SeatCond;
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
    public ResponseEntity<SeatDto> insert(@Valid @RequestBody InsertSeatInfo info) {
        return ResponseEntity.ok(seatService.insert(info));
    }

    @GetMapping("/select/id/{seatId}")
    public ResponseEntity<SeatDto> selectById(@PathVariable("seatId") Long id) {
        return ResponseEntity.ok(seatService.selectById(id));
    }

    @PostMapping("/select")
    public ResponseEntity<PagedModel<SeatDto>> selectByCond(@RequestBody SeatCond cond) {
        return ResponseEntity.ok(new PagedModel<>(seatService.selectByCond(cond)));
    }

    @PutMapping("/update/id/{seatId}")
    public ResponseEntity<SeatDto> update(@PathVariable("seatId") Long seatId, @Valid @RequestBody UpdateSeatInfo info) {
        return ResponseEntity.ok(seatService.update(seatId, info));
    }

    @DeleteMapping("/delete/id/{seatId}")
    public ResponseEntity<SeatDto> delete(@PathVariable("seatId") Long seatId) {
        return ResponseEntity.ok(seatService.delete(seatId));
    }
}
