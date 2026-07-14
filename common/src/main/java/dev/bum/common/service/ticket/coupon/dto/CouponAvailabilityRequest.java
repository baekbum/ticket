package dev.bum.common.service.ticket.coupon.dto;

import jakarta.validation.constraints.NotBlank;
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

    @NotBlank
    private String userId;

    @NotNull
    private Long userCouponId;

    @NotNull
    private Integer orderAmount;
}
