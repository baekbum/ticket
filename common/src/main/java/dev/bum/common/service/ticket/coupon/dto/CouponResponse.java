package dev.bum.common.service.ticket.coupon.dto;

import dev.bum.common.service.ticket.coupon.enums.CouponDiscountType;
import dev.bum.common.service.ticket.coupon.enums.CouponStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CouponResponse {

    private Long couponId;
    private String name;
    private String code;
    private CouponDiscountType discountType;
    private Integer discountValue;
    private Integer maxDiscountAmount;
    private Integer minOrderAmount;
    private String validFrom;
    private String validUntil;
    private Integer validDaysAfterIssue;
    private CouponStatus status;
}
