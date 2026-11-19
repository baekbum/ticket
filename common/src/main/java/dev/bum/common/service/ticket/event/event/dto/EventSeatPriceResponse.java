package dev.bum.common.service.ticket.event.event.dto;

import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventSeatPriceResponse {

    // 좌석 구역 이름.
    private String areaName;

    // 좌석 등급.
    private SeatGrade grade;

    // 가격.
    private Integer price;
}
