package dev.bum.ticket_service.vo.seat;

import dev.bum.ticket_service.enums.SeatStatus;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class UpdateSeatAreaConfig {
    private long id;
    private Integer price;
    private SeatStatus status;
}
