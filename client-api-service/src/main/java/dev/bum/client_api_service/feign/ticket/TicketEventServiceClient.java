package dev.bum.client_api_service.feign.ticket;

import dev.bum.common.service.ticket.event.event.dto.EventCardResponse;
import dev.bum.common.service.ticket.event.event.dto.EventBookingDetailResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "client-ticket-event-service", url = "${services.ticket-service.url}", path = "/api/v1/event")
public interface TicketEventServiceClient {

    @GetMapping("/on-sale/soonest")
    List<EventCardResponse> selectSoonestOnSaleCards();

    @GetMapping("/select/group/{eventGroupCode}")
    EventBookingDetailResponse selectByEventGroupCode(@PathVariable("eventGroupCode") String eventGroupCode);

    @GetMapping("/cards/festival")
    List<EventCardResponse> selectFestivalCards();

    @GetMapping("/cards/open-soon")
    List<EventCardResponse> selectOpenSoonCards();

    @GetMapping("/cards/weekly")
    List<EventCardResponse> selectWeeklyRecommendedCards();

    @GetMapping("/cards/concert")
    List<EventCardResponse> selectConcertCards(
            @RequestParam("sort") String sort,
            @RequestParam("page") int page,
            @RequestParam("size") int size
    );
}
