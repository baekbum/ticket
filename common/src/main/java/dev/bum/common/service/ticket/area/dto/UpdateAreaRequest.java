package dev.bum.common.service.ticket.area.dto;

import dev.bum.common.service.ticket.area.enums.AreaStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateAreaRequest {

    // 좌석 구역 이름.
    private String areaName;

    // 좌석 배치도 저장 키.
    private String layoutKey;

    // 좌석 등급.
    private SeatGrade grade;

    // 가격.
    private Integer price;

    // 좌석 구역 상태.
    private AreaStatus status;
}
