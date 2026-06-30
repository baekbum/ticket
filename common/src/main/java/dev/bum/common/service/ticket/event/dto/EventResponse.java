package dev.bum.common.service.ticket.event.dto;

import dev.bum.common.service.ticket.event.enums.EventStatus;
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
    private String eventDateTime;
    private Integer totalSeats;
    private EventStatus status;
    private Integer maxTicketsPerPerson;
}
