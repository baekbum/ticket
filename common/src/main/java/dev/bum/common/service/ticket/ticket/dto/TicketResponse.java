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

    private long ticketId; // 티켓 번호
    private long seatId;
    private String zone; // 구역
    private Integer seatRow; // 열
    private Integer seatCol; // 번호
    private String seatName; // 좌석 이름
    private String grade; // 좌석 등급
    private int price; // 좌석 가격
    private String status; // 상태
}
