package dev.bum.ticket_service.vo.seat;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeatInfo {
    private long id;
    private String zone;
    private int row;
    private int col;
}
