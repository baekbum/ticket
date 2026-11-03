package dev.bum.common.service.ticket.checkout.dto;

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
public class CheckoutPrepareResponse {

    // 공연 식별자.
    private Long eventId;

    // 주문 식별자.
    private String orderId;

    // 좌석 목록.
    private List<SeatInfo> seats;

    // 중복 처리를 방지하는 멱등성 키.
    private String idempotencyKey;

    // 결제 준비 완료 여부.
    private boolean prepared;

    // 결제 준비 완료 시각.
    private LocalDateTime preparedAt;

    // 만료 시각.
    private LocalDateTime expiresAt;
}
