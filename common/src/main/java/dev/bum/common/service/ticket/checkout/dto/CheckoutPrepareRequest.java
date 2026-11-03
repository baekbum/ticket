package dev.bum.common.service.ticket.checkout.dto;

import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CheckoutPrepareRequest {

    // 주문 식별자.
    @NotBlank
    private String orderId;

    // 공연 식별자.
    @NotNull
    private Long eventId;

    // 좌석 목록.
    @NotNull
    private List<SeatInfo> seats;
}
