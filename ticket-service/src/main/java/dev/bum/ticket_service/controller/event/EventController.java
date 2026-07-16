package dev.bum.ticket_service.controller.event;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.event.event.dto.EventCondRequest;
import dev.bum.common.service.ticket.event.event.dto.EventResponse;
import dev.bum.ticket_service.service.event.event.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequestMapping("/api/v1/event")
@RestController
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping("/select/id/{eventId}")
    public ResponseEntity<EventResponse> selectById(@PathVariable("eventId") Long eventId) {
        return ResponseEntity.ok(eventService.selectVisibleById(eventId));
    }

    @GetMapping("/select")
    public ResponseEntity<CustomPageResponse<EventResponse>> selectByCond(@ModelAttribute EventCondRequest cond) {
        return ResponseEntity.ok(eventService.selectVisibleByCond(cond));
    }
}
