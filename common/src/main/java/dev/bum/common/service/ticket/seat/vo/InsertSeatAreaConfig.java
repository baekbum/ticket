package dev.bum.common.service.ticket.seat.vo;

import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InsertSeatAreaConfig {

    // 좌석 등급.
    @NotNull
    private SeatGrade grade;

    // 좌석 구역.
    @NotBlank
    private String zone;

    // 생성할 좌석 행 수.
    @NotNull
    private Integer rows;

    // 생성할 좌석 열 수.
    @NotNull
    private Integer cols;

    // 가격.
    @NotNull
    private Integer price;

    // 좌석 번호의 시작 행.
    private Integer startRow;

    // 좌석 번호의 시작 열.
    private Integer startCol;

    // 좌석 배치 시작 X 좌표.
    private Double startX;

    // 좌석 배치 시작 Y 좌표.
    private Double startY;

    // 배치도에서 좌석 너비.
    private Double seatWidth;

    // 배치도에서 좌석 높이.
    private Double seatHeight;

    // 좌석 사이의 가로 간격.
    private Double gapX;

    // 좌석 사이의 세로 간격.
    private Double gapY;

    // 좌석 회전 각도.
    private Double rotation;

    // 배치도 회전 각도.
    private Double layoutAngle;
}
