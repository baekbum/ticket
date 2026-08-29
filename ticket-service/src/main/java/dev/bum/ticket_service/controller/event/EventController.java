package dev.bum.ticket_service.controller.event;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.event.event.dto.EventBookingDetailResponse;
import dev.bum.common.service.ticket.event.event.dto.EventCardResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @GetMapping("/select/group/{eventGroupCode}")
    public ResponseEntity<EventBookingDetailResponse> selectByEventGroupCode(@PathVariable("eventGroupCode") String eventGroupCode) {
        return ResponseEntity.ok(eventService.selectBookingDetailByEventGroupCode(eventGroupCode));
    }

    @GetMapping("/select")
    public ResponseEntity<CustomPageResponse<EventResponse>> selectByCond(@ModelAttribute EventCondRequest cond) {
        return ResponseEntity.ok(eventService.selectVisibleByCond(cond));
    }

    @GetMapping("/on-sale/soonest")
    public ResponseEntity<List<EventCardResponse>> selectSoonestOnSaleCards() {
        return ResponseEntity.ok(eventService.selectSoonestOnSaleCards());
    }

    @GetMapping("/cards/festival")
    public ResponseEntity<List<EventCardResponse>> selectFestivalCards() {
        return ResponseEntity.ok(eventService.selectFestivalCards());
    }

    @GetMapping("/cards/open-soon")
    public ResponseEntity<List<EventCardResponse>> selectOpenSoonCards() {
        return ResponseEntity.ok(eventService.selectOpenSoonCards());
    }

    @GetMapping("/cards/weekly")
    public ResponseEntity<List<EventCardResponse>> selectWeeklyRecommendedCards() {
        return ResponseEntity.ok(eventService.selectWeeklyRecommendedCards());
    }

    @GetMapping("/cards/concert")
    public ResponseEntity<List<EventCardResponse>> selectConcertCards(
            @RequestParam(name = "sort", defaultValue = "soonest") String sort
    ) {
        return ResponseEntity.ok(eventService.selectConcertCards(sort));
    }
}
