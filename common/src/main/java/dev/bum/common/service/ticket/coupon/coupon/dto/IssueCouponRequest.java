package dev.bum.common.service.ticket.coupon.coupon.dto;

import jakarta.validation.constraints.NotBlank;
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
public class IssueCouponRequest {

    // 사용자 ID.
    @NotBlank
    private String userId;

    // 쿠폰 식별자.
    @NotNull
    private Long couponId;

    // 만료 시각.
    private LocalDateTime expiresAt;
}
