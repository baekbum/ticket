package dev.bum.common.service.ticket.coupon.dto;

import dev.bum.common.service.ticket.coupon.enums.CouponDiscountType;
import dev.bum.common.service.ticket.coupon.enums.CouponStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateCouponRequest {

    private String name;
    private String code;
    private CouponDiscountType discountType;
    private Integer discountValue;
    private Integer maxDiscountAmount;
    private Integer minOrderAmount;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private CouponStatus status;
}
