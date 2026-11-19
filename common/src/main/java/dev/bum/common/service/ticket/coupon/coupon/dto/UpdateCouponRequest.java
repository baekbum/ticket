package dev.bum.common.service.ticket.coupon.coupon.dto;

import dev.bum.common.service.ticket.coupon.coupon.enums.CouponDiscountType;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateCouponRequest {

    // 이름.
    private String name;

    // 처리 결과 코드.
    private String code;

    // 할인 적용 방식.
    private CouponDiscountType discountType;

    // 할인율 또는 정액 할인 값.
    private Integer discountValue;

    // 최대 할인 가능 금액.
    private Integer maxDiscountAmount;

    // 쿠폰을 적용할 수 있는 최소 주문 금액.
    private Integer minOrderAmount;

    // 사용 가능 시작 시각.
    private LocalDateTime validFrom;

    // 사용 가능 종료 시각.
    private LocalDateTime validUntil;

    // 발급 후 사용 가능한 일수.
    private Integer validDaysAfterIssue;

    // 쿠폰 상태.
    private CouponStatus status;
}
