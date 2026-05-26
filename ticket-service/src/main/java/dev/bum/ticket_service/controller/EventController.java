package dev.bum.ticket_service.controller;


import dev.bum.ticket_service.dto.EventDto;
import dev.bum.ticket_service.service.event.EventService;
import dev.bum.ticket_service.vo.event.EventCond;
import dev.bum.ticket_service.vo.event.InsertEventInfo;
import dev.bum.ticket_service.vo.event.UpdateEventInfo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequestMapping("/api/v1/event")
@RestController
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping("/insert")
    public ResponseEntity<EventDto> insert(@Valid @RequestBody InsertEventInfo info) {
        return ResponseEntity.ok(eventService.insert(info));
    }

    @GetMapping("/select/id/{eventId}")
    public ResponseEntity<EventDto> selectById(@PathVariable("eventId") Long id) {
        return ResponseEntity.ok(eventService.selectById(id));
    }

    @PostMapping("/select")
    public ResponseEntity<PagedModel<EventDto>> selectByCond(@RequestBody EventCond cond) {
        return ResponseEntity.ok(new PagedModel<>(eventService.selectByCond(cond)));
    }

    @PutMapping("/update/id/{eventId}")
    public ResponseEntity<EventDto> update(@PathVariable("eventId") Long eventId, @Valid @RequestBody UpdateEventInfo info) {
        return ResponseEntity.ok(eventService.update(eventId, info));
    }

    @DeleteMapping("/delete/id/{eventId}")
    public ResponseEntity<EventDto> delete(@PathVariable("eventId") Long eventId) {
        return ResponseEntity.ok(eventService.delete(eventId));
    }
}
