package dev.bum.ticket_service.vo.seat;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeatReleaseRequest {
    private Long eventId;
    private String zone;
    private int row;
    private int col;
}
