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

    // 사용자에게 발급된 쿠폰 식별자.
    private Long userCouponId;

    // 할인 적용 방식.
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private DiscountType discountType;

    // 할인 이름.
    @Column(length = 100)
    private String discountName;

    // 쿠폰 할인 방식.
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private CouponDiscountType couponDiscountType;

    // 할인율 또는 정액 할인 값.
    private Integer discountValue;

    // 적용된 할인 금액.
    @Column(nullable = false)
    private int discountAmount;

    // 전달받은 값으로 결제 할인 스냅샷 객체를 초기화한다.
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
