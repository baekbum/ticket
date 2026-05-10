package dev.bum.ticket_service.dto;

import dev.bum.ticket_service.enums.SeatGrade;
import dev.bum.ticket_service.enums.SeatStatus;
import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.jpa.seat.Seat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatDto {
    private Long seatId;
    private String seatNumber;
    private SeatGrade grade;
    private Integer price;
    private SeatStatus status;
    private LocalDateTime time;

    // event 관련
    private Long eventId;
    private String artistName;
    private String title;
    private String venue;
    private LocalDateTime eventDate;

    public SeatDto(Seat seat) {
        this.seatId = seat.getSeatId();
        this.seatNumber = seat.getSeatNumber();
        this.grade = seat.getGrade();
        this.price = seat.getPrice();
        this.status = seat.getStatus();
        this.time = seat.getUpdatedAt();

        // event 관련
        this.eventId = seat.getEvent().getEventId();
        this.artistName = seat.getEvent().getArtistName();
        this.title = seat.getEvent().getTitle();
        this.venue = seat.getEvent().getVenue();
        this.eventDate = seat.getEvent().getEventDate();
    }
}
