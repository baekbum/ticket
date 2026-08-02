package dev.bum.common.service.ticket.event.event.dto;

import dev.bum.common.service.ticket.event.event.enums.EventGenre;
import dev.bum.common.service.ticket.event.event.enums.EventRegion;
import dev.bum.common.service.ticket.event.event.enums.EventTheme;
import dev.bum.common.service.ticket.event.event.enums.TicketLimitScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InsertEventRequest {

    @NotBlank
    private String artistName;

    @NotBlank
    private String title;

    private String eventGroupCode;

    private String description;

    @NotBlank
    private String venue;

    @NotBlank
    private String venueAddress;

    private String posterUrl;

    @NotNull
    private LocalDateTime eventDateTime;

    @NotNull
    private LocalDateTime saleStartAt;

    @NotNull
    private LocalDateTime saleEndAt;

    @NotNull
    private LocalDateTime cancelDeadlineAt;

    @NotNull
    private Integer runningMinutes;

    @NotNull
    private Integer ageLimit;

    @NotNull
    private Integer totalSeats;

    @NotNull
    private Integer maxTicketsPerPerson;

    private TicketLimitScope ticketLimitScope;

    @NotNull
    private EventGenre genre;

    @NotNull
    private EventRegion region;

    @NotNull
    private EventTheme theme;
}
