package dev.bum.common.service.ticket.event.event.dto;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventResponse {

    private Long eventId;
    private String artistName;
    private String title;
    private String description;
    private String venue;
    private String venueAddress;
    private String posterUrl;
    private String eventDateTime;
    private String saleStartAt;
    private String saleEndAt;
    private String cancelDeadlineAt;
    private Integer runningMinutes;
    private Integer ageLimit;
    private Integer totalSeats;
    private Integer availableSeats;
    private EventStatus status;
    private Integer maxTicketsPerPerson;
}
