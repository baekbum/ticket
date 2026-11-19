package dev.bum.common.service.ticket.checkout.dto;

import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.reservation.dto.ReservationDeliveryRequest;
import dev.bum.common.service.ticket.seat.vo.SeatInfo;
import jakarta.validation.Valid;
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
public class CheckoutConfirmRequest {

    // 주문 식별자.
    @NotBlank
    private String orderId;

    // 공연 식별자.
    @NotNull
    private Long eventId;

    // 좌석 목록.
    @NotNull
    private List<SeatInfo> seats;

    // 사용자에게 발급된 쿠폰 식별자.
    private Long userCouponId;

    // 배송 정보.
    @Valid
    // null이면 현장 수령, 값이 있으면 배송 정보 전체를 검증한다.
    private ReservationDeliveryRequest delivery;

    // 결제 수단.
    @NotNull
    private PaymentMethod paymentMethod;

    // 중복 처리를 방지하는 멱등성 키.
    @NotBlank
    private String idempotencyKey;

    // 은행 코드.
    private String bankCode;
}
