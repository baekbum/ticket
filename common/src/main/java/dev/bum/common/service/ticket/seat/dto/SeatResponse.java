package dev.bum.common.service.ticket.seat.dto;

import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatResponse {
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
}
