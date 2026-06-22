package dev.bum.common.service.ticket.event.dto;

import dev.bum.common.service.ticket.event.enums.EventStatus;
import lombok.*;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateEventRequest {
    private String artistName;
    private String title;
    private String description;
    private String venue;
    private LocalDateTime eventDateTime;
    private Integer totalSeats;
    private EventStatus status;
    private Integer maxTicketsPerPerson;
}
