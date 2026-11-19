package dev.bum.common.service.ticket.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketResponse {

    // 티켓 식별자.
    private long ticketId;

    // 좌석 식별자.
    private long seatId;

    // 좌석 구역.
    private String zone;

    // 좌석 행 번호.
    private Integer seatRow;

    // 좌석 열 번호.
    private Integer seatCol;

    // 좌석 표시 이름.
    private String seatName;

    // 등급.
    private String grade;

    // 가격.
    private int price;

    // 티켓 상태.
    private String status;
}
