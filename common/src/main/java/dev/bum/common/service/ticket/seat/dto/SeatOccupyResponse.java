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

    // 주문 식별자.
    private String orderId;

    // 공연 식별자.
    private Long eventId;

    // 사용자 ID.
    private String userId;

    // 좌석 목록.
    private List<SeatInfo> seats;

    // 만료 시각.
    private LocalDateTime expiresAt;
}
