package dev.bum.common.service.ticket.coupon.coupon.dto;

import dev.bum.common.service.ticket.coupon.coupon.enums.CouponDiscountType;
import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserCouponCondRequest {

    private String userId;
    private String couponName;
    private String couponCode;
    private CouponDiscountType discountType;
    private UserCouponStatus status;
    private LocalDate issuedAt;
    private LocalDate expiresAt;
    private LocalDate usedAt;

    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer size = 10;

    private List<String> sort;
}
