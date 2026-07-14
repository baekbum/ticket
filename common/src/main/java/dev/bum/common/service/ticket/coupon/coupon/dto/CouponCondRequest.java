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

    private String name;
    private String code;
    private CouponDiscountType discountType;
    private CouponStatus status;
    private LocalDate validFrom;
    private LocalDate validUntil;
    private Integer validDaysAfterIssue;

    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer size = 10;

    private List<String> sort;
}
