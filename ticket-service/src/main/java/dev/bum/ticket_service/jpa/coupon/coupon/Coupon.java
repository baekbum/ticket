package dev.bum.ticket_service.jpa.coupon.coupon;

import dev.bum.common.service.ticket.coupon.dto.CouponResponse;
import dev.bum.common.service.ticket.coupon.dto.InsertCouponRequest;
import dev.bum.common.service.ticket.coupon.dto.UpdateCouponRequest;
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
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

    // 발급 시각 기준 사용자 쿠폰 만료까지의 일수. null이면 validUntil을 사용한다.
    @Column(name = "valid_days_after_issue")
    private Integer validDaysAfterIssue;

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

    public Coupon(InsertCouponRequest info) {
        this.name = info.getName();
        this.code = info.getCode();
        this.discountType = info.getDiscountType();
        this.discountValue = info.getDiscountValue();
        this.maxDiscountAmount = info.getMaxDiscountAmount();
        this.minOrderAmount = info.getMinOrderAmount();
        this.validFrom = info.getValidFrom();
        this.validUntil = info.getValidUntil();
        this.validDaysAfterIssue = normalizeValidDaysAfterIssue(info.getValidDaysAfterIssue());
        if (this.validDaysAfterIssue != null) this.validUntil = null;
        this.status = info.getStatus() != null ? info.getStatus() : CouponStatus.ACTIVE;
    }

    public void update(UpdateCouponRequest info) {
        if (StringUtils.hasText(info.getName())) this.name = info.getName();
        if (StringUtils.hasText(info.getCode())) this.code = info.getCode();
        if (info.getDiscountType() != null) this.discountType = info.getDiscountType();
        if (info.getDiscountValue() != null) this.discountValue = info.getDiscountValue();
        if (info.getMaxDiscountAmount() != null) this.maxDiscountAmount = info.getMaxDiscountAmount();
        if (info.getMinOrderAmount() != null) this.minOrderAmount = info.getMinOrderAmount();
        if (info.getValidFrom() != null) this.validFrom = info.getValidFrom();
        if (info.getValidUntil() != null) this.validUntil = info.getValidUntil();
        if (info.getValidDaysAfterIssue() != null) {
            this.validDaysAfterIssue = normalizeValidDaysAfterIssue(info.getValidDaysAfterIssue());
            if (this.validDaysAfterIssue != null) this.validUntil = null;
        }
        if (info.getStatus() != null) this.status = info.getStatus();
    }

    public CouponResponse toResponse() {
        return CouponResponse.builder()
                .couponId(this.couponId)
                .name(this.name)
                .code(this.code)
                .discountType(this.discountType)
                .discountValue(this.discountValue)
                .maxDiscountAmount(this.maxDiscountAmount)
                .minOrderAmount(this.minOrderAmount)
                .validFrom(formatDateTime(this.validFrom))
                .validUntil(formatDateTime(this.validUntil))
                .validDaysAfterIssue(this.validDaysAfterIssue)
                .status(this.status)
                .build();
    }

    private Integer normalizeValidDaysAfterIssue(Integer validDaysAfterIssue) {
        return validDaysAfterIssue != null && validDaysAfterIssue > 0 ? validDaysAfterIssue : null;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_TIME_FORMATTER) : null;
    }
}
