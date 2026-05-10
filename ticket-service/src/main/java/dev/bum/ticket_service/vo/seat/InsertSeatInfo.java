package dev.bum.ticket_service.vo.seat;

import dev.bum.ticket_service.enums.SeatGrade;
import dev.bum.ticket_service.jpa.event.Event;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class InsertSeatInfo {

    @NotNull
    private Long eventId;
    private Event event;

    @NotBlank
    private String seatNumber;

    @NotNull
    private SeatGrade grade;

    @NotNull
    private Integer price;
}
