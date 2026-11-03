package dev.bum.common.service.ticket.coupon.coupon.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CouponAvailabilityRequest {

    // 사용자 ID.
    private String userId;

    // 사용자에게 발급된 쿠폰 식별자.
    @NotNull
    private Long userCouponId;

    // 주문 금액.
    @NotNull
    private Integer orderAmount;
}
