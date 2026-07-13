package dev.bum.ticket_service.jpa.coupon;

import dev.bum.common.service.ticket.coupon.enums.CouponDiscountType;
import dev.bum.common.service.ticket.coupon.enums.CouponStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 관리자가 만든 쿠폰 정책 원본.
 */
@Entity
@Table(
        name = "coupons",
        uniqueConstraints = @UniqueConstraint(name = "uk_coupons_code", columnNames = "code")
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_id")
    private Long couponId;

    // 사용자에게 노출되는 쿠폰 이름.
    @Column(nullable = false, length = 100)
    private String name;

    // 쿠폰 식별 코드. 이벤트/관리자 화면에서 쿠폰을 구분할 때 사용한다.
    @Column(nullable = false, length = 50)
    private String code;

    // 정액 할인인지, 퍼센트 할인인지 나타내는 할인 방식.
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 30)
    private CouponDiscountType discountType;

    // 정액이면 원 단위 금액, 퍼센트면 할인율 숫자.
    @Column(name = "discount_value", nullable = false)
    private Integer discountValue;

    // 퍼센트 할인에서 적용할 최대 할인 금액.
    @Column(name = "max_discount_amount")
    private Integer maxDiscountAmount;

    // 쿠폰을 사용할 수 있는 최소 주문 금액.
    @Column(name = "min_order_amount")
    private Integer minOrderAmount;

    // 쿠폰 정책의 사용 시작 시각.
    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    // 쿠폰 정책의 사용 종료 시각.
    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    // 쿠폰 정책의 활성/비활성/만료 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CouponStatus status;

    // 쿠폰 정책이 생성된 시각.
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 쿠폰 정책이 마지막으로 수정된 시각.
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
