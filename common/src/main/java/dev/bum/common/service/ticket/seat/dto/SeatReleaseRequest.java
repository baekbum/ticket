package dev.bum.common.service.ticket.seat.dto;

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
