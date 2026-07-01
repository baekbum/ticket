package dev.bum.admin_service.feign.event;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.event.dto.EventCondRequest;
import dev.bum.common.service.ticket.event.dto.EventResponse;
import dev.bum.common.service.ticket.event.dto.InsertEventRequest;
import dev.bum.common.service.ticket.event.dto.UpdateEventRequest;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(name = "event-service", url = "${services.ticket-service.url}", path = "/api/v1/event")
public interface EventServiceClient {

    @PostMapping(value = "/insert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    EventResponse insert(
            @RequestPart("event") String event,
            @RequestPart(value = "posterImage", required = false) MultipartFile posterImage
    );

    @GetMapping("/select/id/{eventId}")
    EventResponse selectById(@PathVariable("eventId") Long eventId);

    @PostMapping("/select")
    CustomPageResponse<EventResponse> selectByCond(@RequestBody EventCondRequest cond);

    @PutMapping(value = "/update/id/{eventId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    EventResponse update(@PathVariable("eventId") Long eventId, @Valid @RequestBody UpdateEventRequest info);

    @PutMapping(value = "/update/id/{eventId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    EventResponse update(
            @PathVariable("eventId") Long eventId,
            @RequestPart("event") String event,
            @RequestPart(value = "posterImage", required = false) MultipartFile posterImage
    );

    @DeleteMapping("/delete/id/{eventId}")
    EventResponse delete(@PathVariable("eventId") Long eventId);
}
