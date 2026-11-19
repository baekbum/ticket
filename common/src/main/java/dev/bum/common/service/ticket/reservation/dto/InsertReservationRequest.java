package dev.bum.common.service.ticket.reservation.dto;

import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InsertReservationRequest {

    // 주문 식별자.
    @NotBlank
    private String orderId;

    // 사용자 ID.
    @NotBlank
    private String userId;

    // 좌석 목록.
    @NotNull
    private List<SeatInfo> seats;

    // 공연 식별자.
    @NotNull
    private Long eventId;

    // 사용자에게 발급된 쿠폰 식별자.
    private Long userCouponId;
}
