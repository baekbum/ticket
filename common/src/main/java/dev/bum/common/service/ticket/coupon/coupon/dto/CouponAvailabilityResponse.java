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

    // 사용 또는 예매 가능 여부.
    private boolean available;

    // 적용된 할인 금액.
    private Integer discountAmount;

    // 처리 사유.
    private String reason;
}
