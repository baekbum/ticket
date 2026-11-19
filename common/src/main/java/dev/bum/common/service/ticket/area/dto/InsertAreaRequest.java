package dev.bum.common.service.ticket.area.dto;

import dev.bum.common.service.ticket.area.enums.AreaStatus;
import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InsertAreaRequest {

    // 공연 식별자.
    @NotNull
    private Long eventId;

    // 좌석 구역 이름.
    @NotBlank
    private String areaName;

    // 좌석 배치도 저장 키.
    private String layoutKey;

    // 좌석 등급.
    @NotNull
    private SeatGrade grade;

    // 가격.
    @NotNull
    private Integer price;

    // 좌석 구역 상태.
    private AreaStatus status;
}
