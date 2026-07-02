package dev.bum.common.service.ticket.seat.vo;

import dev.bum.common.service.ticket.seat.enums.SeatStatus;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateSeatAreaConfig {
    private long id;
    private Integer price;
    private SeatStatus status;
    private Double positionX;
    private Double positionY;
    private Double rotation;

    @Override
    public String toString() {
        return "UpdateSeatAreaConfig{" +
                "id=" + id +
                ", price=" + price +
                ", status=" + status +
                ", positionX=" + positionX +
                ", positionY=" + positionY +
                ", rotation=" + rotation +
                '}';
    }
}
