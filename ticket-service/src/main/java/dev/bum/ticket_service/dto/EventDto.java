package dev.bum.ticket_service.dto;

import dev.bum.ticket_service.enums.EventStatus;
import dev.bum.ticket_service.jpa.event.Event;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
    private String eventDateTime;
    private Integer totalSeats;
    private EventStatus status;
    private Integer maxTicketsPerPerson;


    public EventDto(Event event) {
        this.eventId = event.getEventId();
        this.artistName = event.getArtistName();
        this.title = event.getTitle();
        this.description = event.getDescription();
        this.venue = event.getVenue();

        DateTimeFormatter eventFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH시");
        this.eventDateTime = event.getEventDateTime().format(eventFormatter);

        this.totalSeats = event.getTotalSeats();
        this.status = event.getStatus();
        this.maxTicketsPerPerson = event.getMaxTicketsPerPerson();
    }
}
