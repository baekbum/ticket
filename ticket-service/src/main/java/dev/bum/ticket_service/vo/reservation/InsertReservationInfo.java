package dev.bum.ticket_service.vo.reservation;

import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class InsertReservationInfo {

    @NotBlank
    private String userId;

    @NotNull
    private List<Long> seatIdList;

    @NotNull
    private Long eventId;

    private Event event;
}
