package dev.bum.common.service.ticket.seat.vo;

import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateSeatAreaConfig {

    // 고유 식별자.
    private long id;

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

    // 객체의 문자열 표현을 반환한다.
    @Override
    public String toString() {

        return "UpdateSeatAreaConfig{" +
                "id=" + id +
                ", price=" + price +
                ", status=" + status +
                ", positionX=" + positionX +
                ", positionY=" + positionY +
                ", seatWidth=" + seatWidth +
                ", seatHeight=" + seatHeight +
                ", rotation=" + rotation +
                ", layoutAngle=" + layoutAngle +
                '}';
    }
}
