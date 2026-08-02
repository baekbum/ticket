package dev.bum.common.service.ticket.event.event.dto;

import dev.bum.common.service.ticket.event.event.enums.EventGenre;
import dev.bum.common.service.ticket.event.event.enums.EventRegion;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.common.service.ticket.event.event.enums.EventTheme;
import dev.bum.common.service.ticket.event.event.enums.TicketLimitScope;
import lombok.*;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateEventRequest {
    private String artistName;
    private String title;
    private String eventGroupCode;
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
    private TicketLimitScope ticketLimitScope;
    private EventGenre genre;
    private EventRegion region;
    private EventTheme theme;
}
