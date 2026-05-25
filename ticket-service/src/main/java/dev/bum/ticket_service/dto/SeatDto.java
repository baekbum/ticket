package dev.bum.ticket_service.dto;

import dev.bum.ticket_service.enums.SeatGrade;
import dev.bum.ticket_service.enums.SeatStatus;
import dev.bum.ticket_service.jpa.seat.Seat;
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
public class SeatDto {
    private Long seatId;
    private String zone;
    private Integer seatRow;
    private Integer seatCol;
    private String seatName;
    private SeatGrade grade;
    private Integer price;
    private SeatStatus status;

    // event 관련
    private Long eventId;
    private String artistName;
    private String title;
    private String venue;
    private String eventDateTime;

    public SeatDto(Seat seat) {
        this.seatId = seat.getSeatId();
        this.zone = seat.getZone();
        this.seatRow = seat.getSeatRow();
        this.seatCol = seat.getSeatCol();
        this.seatName = String.format("%s %d열 %d번", this.zone, this.seatRow, this.seatCol);
        this.grade = seat.getGrade();
        this.price = seat.getPrice();
        this.status = seat.getStatus();

        // event 관련
        this.eventId = seat.getEvent().getEventId();
        this.artistName = seat.getEvent().getArtistName();
        this.title = seat.getEvent().getTitle();
        this.venue = seat.getEvent().getVenue();

        DateTimeFormatter eventFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH시");
        this.eventDateTime = seat.getEvent().getEventDateTime().format(eventFormatter);
    }
}
