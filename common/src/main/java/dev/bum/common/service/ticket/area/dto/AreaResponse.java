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
public class AreaResponse {

    // 좌석 구역 식별자.
    private Long areaId;

    // 공연 식별자.
    private Long eventId;

    // 공연 제목.
    private String eventTitle;

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
