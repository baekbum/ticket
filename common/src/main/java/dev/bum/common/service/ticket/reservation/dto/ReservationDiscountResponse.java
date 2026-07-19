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

    private Long reservationDiscountId;
    private Long userCouponId;
    private DiscountType discountType;
    private String discountName;
    private CouponDiscountType couponDiscountType;
    private Integer discountValue;
    private Integer discountAmount;
    private String createdAt;
}
