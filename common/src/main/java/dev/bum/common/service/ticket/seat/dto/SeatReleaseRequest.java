package dev.bum.common.service.ticket.seat.dto;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeatReleaseRequest {

    // 공연 식별자.
    private Long eventId;

    // 좌석 구역.
    private String zone;

    // 행 번호.
    private int row;

    // 열 번호.
    private int col;
}
