package dev.bum.common.service.ticket.seat.vo;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeatInfo {

    // 고유 식별자.
    private long id;

    // 좌석 구역.
    private String zone;

    // 행 번호.
    private int row;

    // 열 번호.
    private int col;
}
