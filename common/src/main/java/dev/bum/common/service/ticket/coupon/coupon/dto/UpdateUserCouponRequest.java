package dev.bum.common.service.ticket.coupon.coupon.dto;

import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateUserCouponRequest {

    // 사용자 쿠폰 상태.
    @NotNull
    private UserCouponStatus status;

    // 만료 시각.
    private LocalDateTime expiresAt;
}
