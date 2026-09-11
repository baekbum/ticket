package dev.bum.client_api_service.controller.event;

import dev.bum.client_api_service.feign.ticket.TicketEventServiceClient;
import dev.bum.common.service.ticket.event.event.dto.EventBookingDetailResponse;
import dev.bum.common.service.ticket.event.event.dto.EventCardResponse;
import dev.bum.common.service.ticket.event.event.enums.EventGenre;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/event")
@RequiredArgsConstructor
public class ClientEventController {

    private final TicketEventServiceClient ticketEventServiceClient;

    @GetMapping("/on-sale/soonest")
    public ResponseEntity<List<EventCardResponse>> selectSoonestOnSaleCards() {
        return ResponseEntity.ok(ticketEventServiceClient.selectSoonestOnSaleCards());
    }

    @GetMapping("/select/group/{eventGroupCode}")
    public ResponseEntity<EventBookingDetailResponse> selectByEventGroupCode(@PathVariable("eventGroupCode") String eventGroupCode) {
        return ResponseEntity.ok(ticketEventServiceClient.selectByEventGroupCode(eventGroupCode));
    }

    @GetMapping("/cards/festival")
    public ResponseEntity<List<EventCardResponse>> selectFestivalCards() {
        return ResponseEntity.ok(ticketEventServiceClient.selectFestivalCards());
    }

    @GetMapping("/cards/open-soon")
    public ResponseEntity<List<EventCardResponse>> selectOpenSoonCards() {
        return ResponseEntity.ok(ticketEventServiceClient.selectOpenSoonCards());
    }

    @GetMapping("/cards/weekly")
    public ResponseEntity<List<EventCardResponse>> selectWeeklyRecommendedCards() {
        return ResponseEntity.ok(ticketEventServiceClient.selectWeeklyRecommendedCards());
    }

    @GetMapping("/cards/concert")
    public ResponseEntity<List<EventCardResponse>> selectConcertCards(
            @RequestParam(name = "sort", defaultValue = "soonest") String sort,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "5") int size
    ) {
        return ResponseEntity.ok(ticketEventServiceClient.selectConcertCards(sort, page, size));
    }

    @GetMapping("/cards/genre/{genre}")
    public ResponseEntity<List<EventCardResponse>> selectGenreCards(
            @PathVariable("genre") EventGenre genre,
            @RequestParam(name = "sort", defaultValue = "soonest") String sort,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "5") int size
    ) {
        return ResponseEntity.ok(ticketEventServiceClient.selectGenreCards(genre, sort, page, size));
    }
}
