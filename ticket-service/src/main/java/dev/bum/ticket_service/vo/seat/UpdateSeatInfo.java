package dev.bum.ticket_service.vo.seat;

import dev.bum.ticket_service.enums.SeatGrade;
import dev.bum.ticket_service.enums.SeatStatus;
import lombok.*;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class UpdateSeatInfo {
    private String seatNumber;
    private SeatGrade grade;
    private Integer price;
    private SeatStatus status;
}
