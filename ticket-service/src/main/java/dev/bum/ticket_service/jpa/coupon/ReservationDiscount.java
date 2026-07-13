package dev.bum.ticket_service.jpa.coupon;

import dev.bum.common.service.ticket.coupon.enums.CouponDiscountType;
import dev.bum.common.service.ticket.coupon.enums.DiscountType;
import dev.bum.ticket_service.jpa.reservation.Reservation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 예매에 실제 적용된 할인 결과 스냅샷.
 */
@Entity
@Table(name = "reservation_discounts")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationDiscount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_discount_id")
    private Long reservationDiscountId;

    // 할인이 적용된 예매.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    // 할인에 사용된 사용자 쿠폰. 쿠폰이 아닌 할인 수단이면 null일 수 있다.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_coupon_id")
    private UserCoupon userCoupon;

    // 예매에 적용된 할인 종류.
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 30)
    private DiscountType discountType;

    // 예매 당시의 할인 이름 스냅샷.
    @Column(name = "discount_name", nullable = false, length = 100)
    private String discountName;

    // 쿠폰 할인 방식 스냅샷.
    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_discount_type", length = 30)
    private CouponDiscountType couponDiscountType;

    // 예매 당시 할인 기준값. 정액이면 금액, 퍼센트면 할인율 숫자.
    @Column(name = "discount_value")
    private Integer discountValue;

    // 예매에 실제 적용된 최종 할인 금액.
    @Column(name = "discount_amount", nullable = false)
    private Integer discountAmount;

    // 할인 기록이 생성된 시각.
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
