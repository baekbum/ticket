package dev.bum.ticket_service.jpa.coupon;

import dev.bum.common.service.ticket.coupon.enums.UserCouponStatus;
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
 * 특정 사용자가 실제로 보유한 쿠폰.
 */
@Entity
@Table(
        name = "user_coupons",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_coupons_user_coupon", columnNames = {"user_id", "coupon_id"})
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_coupon_id")
    private Long userCouponId;

    // 쿠폰을 보유한 사용자 ID.
    @Column(name = "user_id", nullable = false)
    private String userId;

    // 이 사용자가 발급받은 쿠폰 정책.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    // 사용자 쿠폰의 발급/사용/만료/취소 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserCouponStatus status;

    // 사용자에게 쿠폰이 발급된 시각.
    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    // 사용자가 예매에 쿠폰을 사용한 시각.
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    // 사용자 쿠폰의 만료 시각.
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    // 사용자 쿠폰 row가 생성된 시각.
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 사용자 쿠폰 상태가 마지막으로 변경된 시각.
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
