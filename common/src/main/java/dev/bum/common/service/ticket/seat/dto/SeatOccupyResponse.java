package dev.bum.common.service.ticket.seat.dto;

import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeatOccupyResponse {

    private String orderId;
    private Long eventId;
    private String userId;
    private List<SeatInfo> seats;
    private LocalDateTime expiresAt;
}
