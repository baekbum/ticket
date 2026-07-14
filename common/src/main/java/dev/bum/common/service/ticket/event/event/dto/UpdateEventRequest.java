package dev.bum.common.service.ticket.event.event.dto;

import dev.bum.common.service.ticket.event.event.enums.EventStatus;
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
    private String venueAddress;
    private String posterUrl;
    private LocalDateTime eventDateTime;
    private LocalDateTime saleStartAt;
    private LocalDateTime saleEndAt;
    private LocalDateTime cancelDeadlineAt;
    private Integer runningMinutes;
    private Integer ageLimit;
    private Integer totalSeats;
    private Integer availableSeats;
    private EventStatus status;
    private Integer maxTicketsPerPerson;
}
