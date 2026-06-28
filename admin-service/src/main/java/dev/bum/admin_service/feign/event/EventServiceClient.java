package dev.bum.admin_service.feign.event;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.event.dto.EventCondRequest;
import dev.bum.common.service.ticket.event.dto.EventResponse;
import dev.bum.common.service.ticket.event.dto.InsertEventRequest;
import dev.bum.common.service.ticket.event.dto.UpdateEventRequest;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "event-service", url = "${services.ticket-service.url}", path = "/api/v1/event")
public interface EventServiceClient {

    @PostMapping("/insert")
    EventResponse insert(@RequestBody InsertEventRequest info);

    @GetMapping("/select/id/{eventId}")
    EventResponse selectById(@PathVariable("eventId") Long eventId);

    @PostMapping("/select")
    CustomPageResponse<EventResponse> selectByCond(@RequestBody EventCondRequest cond);

    @PutMapping("/update/id/{eventId}")
    EventResponse update(@PathVariable("eventId") Long eventId, @Valid @RequestBody UpdateEventRequest info);

    @DeleteMapping("/delete/id/{eventId}")
    EventResponse delete(@PathVariable("eventId") Long eventId);
}

