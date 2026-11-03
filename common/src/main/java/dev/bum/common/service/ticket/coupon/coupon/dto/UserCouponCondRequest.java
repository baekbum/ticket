package dev.bum.common.service.ticket.coupon.coupon.dto;

import dev.bum.common.service.ticket.coupon.coupon.enums.CouponDiscountType;
import dev.bum.common.service.ticket.coupon.coupon.enums.UserCouponStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserCouponCondRequest {

    // 사용자 ID.
    private String userId;

    // 쿠폰 이름.
    private String couponName;

    // 쿠폰 코드.
    private String couponCode;

    // 할인 적용 방식.
    private CouponDiscountType discountType;

    // 사용자 쿠폰 상태.
    private UserCouponStatus status;

    // 발급 시각.
    private LocalDate issuedAt;

    // 만료 시각.
    private LocalDate expiresAt;

    // 사용 시각.
    private LocalDate usedAt;

    // 조회할 페이지.
    @Builder.Default
    private Integer page = 0;

    // 페이지당 조회 건수.
    @Builder.Default
    private Integer size = 10;

    // 조회 결과 정렬 조건.
    private List<String> sort;
}
