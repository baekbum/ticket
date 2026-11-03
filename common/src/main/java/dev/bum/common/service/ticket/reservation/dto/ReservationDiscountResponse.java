package dev.bum.common.service.ticket.reservation.dto;

import dev.bum.common.service.ticket.coupon.coupon.enums.CouponDiscountType;
import dev.bum.common.service.ticket.coupon.coupon.enums.DiscountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationDiscountResponse {

    // 예매 할인 식별자.
    private Long reservationDiscountId;

    // 사용자에게 발급된 쿠폰 식별자.
    private Long userCouponId;

    // 할인 적용 방식.
    private DiscountType discountType;

    // 할인 이름.
    private String discountName;

    // 쿠폰 할인 방식.
    private CouponDiscountType couponDiscountType;

    // 할인율 또는 정액 할인 값.
    private Integer discountValue;

    // 적용된 할인 금액.
    private Integer discountAmount;

    // 생성 시각.
    private String createdAt;
}
