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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RequestMapping("/api/v1/event")
@RestController
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping(value = "/insert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EventResponse> insert(
            @Valid @RequestPart("event") InsertEventRequest info,
            @RequestPart(value = "posterImage", required = false) MultipartFile posterImage
    ) {
        return ResponseEntity.ok(eventService.insert(info, posterImage));
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

    @PutMapping(value = "/update/id/{eventId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EventResponse> update(
            @PathVariable("eventId") Long eventId,
            @Valid @RequestPart("event") UpdateEventRequest info,
            @RequestPart(value = "posterImage", required = false) MultipartFile posterImage
    ) {
        return ResponseEntity.ok(eventService.update(eventId, info, posterImage));
    }

    @DeleteMapping("/delete/id/{eventId}")
    public ResponseEntity<EventResponse> delete(@PathVariable("eventId") Long eventId) {
        return ResponseEntity.ok(eventService.delete(eventId));
    }
}
