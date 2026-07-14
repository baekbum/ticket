package dev.bum.common.service.ticket.coupon.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CouponAvailabilityResponse {

    private boolean available;
    private Integer discountAmount;
    private String reason;
}
