package dev.bum.common.service.ticket.coupon.dto;

import dev.bum.common.service.ticket.coupon.enums.CouponDiscountType;
import dev.bum.common.service.ticket.coupon.enums.CouponStatus;
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
public class InsertCouponRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String code;

    @NotNull
    private CouponDiscountType discountType;

    @NotNull
    private Integer discountValue;

    private Integer maxDiscountAmount;
    private Integer minOrderAmount;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private Integer validDaysAfterIssue;
    private CouponStatus status;
}
