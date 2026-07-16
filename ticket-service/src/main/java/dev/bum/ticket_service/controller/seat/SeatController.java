package dev.bum.ticket_service.controller.seat;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.seat.dto.SeatCondRequest;
import dev.bum.common.service.ticket.seat.dto.SeatOccupyRequest;
import dev.bum.common.service.ticket.seat.dto.SeatOccupyResponse;
import dev.bum.common.service.ticket.seat.dto.SeatResponse;
import dev.bum.ticket_service.service.seat.SeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequestMapping("/api/v1/seat")
@RestController
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @GetMapping("/select/id/{seatId}")
    public ResponseEntity<SeatResponse> selectById(@PathVariable("seatId") Long id) {
        return ResponseEntity.ok(seatService.selectById(id));
    }

    @GetMapping("/select")
    public ResponseEntity<CustomPageResponse<SeatResponse>> selectByCond(@ModelAttribute SeatCondRequest cond) {
        return ResponseEntity.ok(seatService.selectByCond(cond));
    }

    @PostMapping("/occupy")
    public ResponseEntity<SeatOccupyResponse> occupySeat(
            @AuthenticationPrincipal String currentUserId,
            @RequestBody SeatOccupyRequest request
    ) {
        request.setUserId(currentUserId);
        return ResponseEntity.ok(seatService.occupySeat(request));
    }
}
