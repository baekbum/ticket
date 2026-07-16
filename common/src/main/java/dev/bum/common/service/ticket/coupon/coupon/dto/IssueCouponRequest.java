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

    @NotBlank
    private String userId;

    @NotNull
    private Long couponId;

    private LocalDateTime expiresAt;
}
