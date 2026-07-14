package dev.bum.common.service.ticket.coupon.coupon.dto;

import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserCouponResponse {

    private Long userCouponId;
    private String userId;
    private CouponResponse coupon;
    private UserCouponStatus status;
    private String issuedAt;
    private String usedAt;
    private String expiresAt;
}
