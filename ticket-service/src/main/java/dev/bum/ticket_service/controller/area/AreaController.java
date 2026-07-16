package dev.bum.ticket_service.controller.area;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.area.dto.AreaCondRequest;
import dev.bum.common.service.ticket.area.dto.AreaResponse;
import dev.bum.common.service.ticket.event.eventLayout.dto.EventLayoutResponse;
import dev.bum.ticket_service.service.area.AreaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequestMapping("/api/v1/area")
@RestController
@RequiredArgsConstructor
public class AreaController {

    private final AreaService areaService;

    @GetMapping("/layout/event/{eventId}")
    public ResponseEntity<EventLayoutResponse> selectLayout(@PathVariable("eventId") Long eventId) {
        EventLayoutResponse response = areaService.selectLayout(eventId);
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.noContent().build();
    }

    @GetMapping("/select/id/{areaId}")
    public ResponseEntity<AreaResponse> selectById(@PathVariable("areaId") Long areaId) {
        return ResponseEntity.ok(areaService.selectById(areaId));
    }

    @GetMapping("/select")
    public ResponseEntity<CustomPageResponse<AreaResponse>> selectByCond(@ModelAttribute AreaCondRequest cond) {
        return ResponseEntity.ok(areaService.selectByCond(cond));
    }
}
