package dev.bum.ticket_service.controller.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.event.event.dto.DeleteEventBulkRequest;
import dev.bum.common.service.ticket.event.event.dto.EventCondRequest;
import dev.bum.common.service.ticket.event.event.dto.EventResponse;
import dev.bum.common.service.ticket.event.event.dto.InsertEventRequest;
import dev.bum.common.service.ticket.event.event.dto.UpdateEventRequest;
import dev.bum.ticket_service.service.event.event.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RequestMapping("/api/v1/manage/event")
@RestController
@RequiredArgsConstructor
public class EventManagementController {

    private final EventService eventService;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/insert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EventResponse> insert(
            @RequestPart("event") String event,
            @RequestPart(value = "posterImage", required = false) MultipartFile posterImage
    ) {
        return ResponseEntity.ok(eventService.insert(readEvent(event, InsertEventRequest.class), posterImage));
    }

    @GetMapping("/select/id/{eventId}")
    public ResponseEntity<EventResponse> selectById(@PathVariable("eventId") Long eventId) {
        return ResponseEntity.ok(eventService.selectById(eventId));
    }

    @PostMapping("/select")
    public ResponseEntity<CustomPageResponse<EventResponse>> selectByCond(@RequestBody EventCondRequest cond) {
        return ResponseEntity.ok(eventService.selectByCond(cond));
    }

    @PutMapping(value = "/update/id/{eventId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EventResponse> update(@PathVariable("eventId") Long eventId, @Valid @RequestBody UpdateEventRequest info) {
        return ResponseEntity.ok(eventService.update(eventId, info));
    }

    @PutMapping(value = "/update/id/{eventId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EventResponse> update(
            @PathVariable("eventId") Long eventId,
            @RequestPart("event") String event,
            @RequestPart(value = "posterImage", required = false) MultipartFile posterImage
    ) {
        return ResponseEntity.ok(eventService.update(eventId, readEvent(event, UpdateEventRequest.class), posterImage));
    }

    @DeleteMapping("/delete/id/{eventId}")
    public ResponseEntity<EventResponse> delete(@PathVariable("eventId") Long eventId) {
        return ResponseEntity.ok(eventService.delete(eventId));
    }

    @DeleteMapping("/delete/bulk")
    public ResponseEntity<Void> deleteBulk(@Valid @RequestBody DeleteEventBulkRequest info) {
        eventService.deleteBulk(info);
        return ResponseEntity.ok().build();
    }

    private <T> T readEvent(String event, Class<T> type) {
        try {
            return objectMapper.readValue(event, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid event payload.", e);
        }
    }
}
