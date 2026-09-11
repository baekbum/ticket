package dev.bum.common.service.ticket.event.event.dto;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.event.event.enums.TicketLimitScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventBookingDetailResponse {

    private String eventGroupCode;
    private String artistName;
    private String title;
    private String description;
    private String venue;
    private String venueAddress;
    private String posterUrl;
    private String eventDateRange;
    private String saleStartAt;
    private String saleEndAt;
    private Integer runningMinutes;
    private Integer ageLimit;
    private Integer totalSeats;
    private Integer availableSeats;
    private EventStatus status;
    private Integer maxTicketsPerPerson;
    private TicketLimitScope ticketLimitScope;
    private String bookingStatus;
    private String bookingMessage;
    private List<EventScheduleResponse> schedules;
}
