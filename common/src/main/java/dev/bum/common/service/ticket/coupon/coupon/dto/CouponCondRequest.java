package dev.bum.common.service.ticket.coupon.coupon.dto;

import dev.bum.common.service.ticket.coupon.coupon.enums.CouponStatus;
import dev.bum.common.service.ticket.coupon.coupon.enums.CouponDiscountType;
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
public class CouponCondRequest {

    // 이름.
    private String name;

    // 처리 결과 코드.
    private String code;

    // 할인 적용 방식.
    private CouponDiscountType discountType;

    // 쿠폰 상태.
    private CouponStatus status;

    // 사용 가능 시작 시각.
    private LocalDate validFrom;

    // 사용 가능 종료 시각.
    private LocalDate validUntil;

    // 발급 후 사용 가능한 일수.
    private Integer validDaysAfterIssue;

    // 조회할 페이지.
    @Builder.Default
    private Integer page = 0;

    // 페이지당 조회 건수.
    @Builder.Default
    private Integer size = 10;

    // 조회 결과 정렬 조건.
    private List<String> sort;
}
