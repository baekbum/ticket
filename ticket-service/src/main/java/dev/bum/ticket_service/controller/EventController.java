package dev.bum.ticket_service.controller;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.event.dto.EventResponse;
import dev.bum.ticket_service.service.event.EventService;
import dev.bum.common.service.ticket.event.dto.EventCondRequest;
import dev.bum.common.service.ticket.event.dto.InsertEventRequest;
import dev.bum.common.service.ticket.event.dto.UpdateEventRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequestMapping("/api/v1/event")
@RestController
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping("/insert")
    public ResponseEntity<EventResponse> insert(@Valid @RequestBody InsertEventRequest info) {
        return ResponseEntity.ok(eventService.insert(info));
    }

    @GetMapping("/select/id/{eventId}")
    public ResponseEntity<EventResponse> selectById(@PathVariable("eventId") Long eventId) {
        return ResponseEntity.ok(eventService.selectById(eventId));
    }

    @PostMapping("/select")
    public ResponseEntity<CustomPageResponse<EventResponse>> selectByCond(@RequestBody EventCondRequest cond) {
        return ResponseEntity.ok(eventService.selectByCond(cond));
    }

    @PutMapping("/update/id/{eventId}")
    public ResponseEntity<EventResponse> update(@PathVariable("eventId") Long eventId, @Valid @RequestBody UpdateEventRequest info) {
        return ResponseEntity.ok(eventService.update(eventId, info));
    }

    @DeleteMapping("/delete/id/{eventId}")
    public ResponseEntity<EventResponse> delete(@PathVariable("eventId") Long eventId) {
        return ResponseEntity.ok(eventService.delete(eventId));
    }
}
