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

    // 사용자에게 발급된 쿠폰 식별자.
    private Long userCouponId;

    // 사용자 ID.
    private String userId;

    // 쿠폰 정보.
    private CouponResponse coupon;

    // 사용자 쿠폰 상태.
    private UserCouponStatus status;

    // 발급 시각.
    private String issuedAt;

    // 사용 시각.
    private String usedAt;

    // 만료 시각.
    private String expiresAt;
}
