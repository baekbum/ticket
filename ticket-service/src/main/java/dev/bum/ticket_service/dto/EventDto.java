package dev.bum.ticket_service.dto;

import dev.bum.ticket_service.enums.EventStatus;
import dev.bum.ticket_service.jpa.event.Event;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventDto {

    private Long eventId;
    private String artistName;
    private String title;
    private String description;
    private String venue;
    private LocalDateTime eventDate;
    private Integer totalSeats;
    private EventStatus status;

    public EventDto(Event event) {
        this.eventId = event.getEventId();
        this.artistName = event.getArtistName();
        this.title = event.getTitle();
        this.description = event.getDescription();
        this.venue = event.getVenue();
        this.eventDate = event.getEventDate();
        this.totalSeats = event.getTotalSeats();
        this.status = event.getStatus();
    }
}
