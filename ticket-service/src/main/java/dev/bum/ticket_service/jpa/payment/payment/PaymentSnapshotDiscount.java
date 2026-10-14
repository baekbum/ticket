package dev.bum.ticket_service.jpa.payment.payment;

import dev.bum.common.service.ticket.coupon.coupon.enums.*;
import dev.bum.ticket_service.jpa.reservation.reservationDiscount.ReservationDiscount;

import jakarta.persistence.*;

import lombok.*;

/** 결제 확정 당시 적용한 할인 정책과 금액. */
@Embeddable
@Getter
@NoArgsConstructor
public class PaymentSnapshotDiscount {
    private Long userCouponId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private DiscountType discountType;

    @Column(length = 100)
    private String discountName;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private CouponDiscountType couponDiscountType;

    private Integer discountValue;

    @Column(nullable = false)
    private int discountAmount;

    public PaymentSnapshotDiscount(ReservationDiscount discount) {
        userCouponId = discount.getUserCoupon() == null
                ? null
                : discount.getUserCoupon().getUserCouponId();
        discountType = discount.getDiscountType();
        discountName = discount.getDiscountName();
        couponDiscountType = discount.getCouponDiscountType();
        discountValue = discount.getDiscountValue();
        discountAmount = discount.getDiscountAmount() == null ? 0 : discount.getDiscountAmount();
    }
}
