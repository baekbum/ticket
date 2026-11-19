package dev.bum.common.service.ticket.seat.dto;

import dev.bum.common.service.ticket.seat.enums.SeatGrade;
import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatResponse {

    // 좌석 식별자.
    private Long seatId;

    // 좌석 구역.
    private String zone;

    // 좌석 행 번호.
    private Integer seatRow;

    // 좌석 열 번호.
    private Integer seatCol;

    // 좌석 표시 이름.
    private String seatName;

    // 좌석 등급.
    private SeatGrade grade;

    // 가격.
    private Integer price;

    // 좌석 상태.
    private SeatStatus status;

    // 배치도에서 좌석의 X 좌표.
    private Double positionX;

    // 배치도에서 좌석의 Y 좌표.
    private Double positionY;

    // 배치도에서 좌석 너비.
    private Double seatWidth;

    // 배치도에서 좌석 높이.
    private Double seatHeight;

    // 좌석 회전 각도.
    private Double rotation;

    // 배치도 회전 각도.
    private Double layoutAngle;

    // 공연 식별자.
    private Long eventId;

    // 좌석 구역 식별자.
    private Long areaId;

    // 좌석 구역 이름.
    private String areaName;

    // 공연 아티스트 이름.
    private String artistName;

    // 제목.
    private String title;

    // 공연장 이름.
    private String venue;

    // 공연 시작 일시.
    private String eventDateTime;
}
