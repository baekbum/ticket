package dev.bum.ticket_service.jpa.checkout;

import dev.bum.ticket_service.jpa.payment.Payment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "checkout_attempts",
        indexes = {
                @Index(name = "idx_checkout_attempts_status_expires_at", columnList = "status,expires_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_checkout_attempts_order_id", columnNames = "order_id"),
                @UniqueConstraint(name = "uk_checkout_attempts_payment_no", columnNames = "payment_no"),
                @UniqueConstraint(name = "uk_checkout_attempts_payment_id", columnNames = "payment_id")
        }
)
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CheckoutAttempt {

    @Id
    @Column(name = "idempotency_key", length = 100, nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "user_id", length = 100, nullable = false, updatable = false)
    private String userId;

    @Column(name = "order_id", length = 50, nullable = false, updatable = false)
    private String orderId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private Long eventId;

    @Column(name = "payment_no", length = 60, nullable = false, updatable = false)
    private String paymentNo;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CheckoutAttemptStatus status;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static CheckoutAttempt prepare(
            String idempotencyKey,
            String userId,
            String orderId,
            Long eventId,
            String paymentNo,
            LocalDateTime expiresAt
    ) {
        return CheckoutAttempt.builder()
                .idempotencyKey(idempotencyKey)
                .userId(userId)
                .orderId(orderId)
                .eventId(eventId)
                .paymentNo(paymentNo)
                .status(CheckoutAttemptStatus.PREPARED)
                .expiresAt(expiresAt)
                .build();
    }

    public void confirm(Payment payment) {
        if (status != CheckoutAttemptStatus.PREPARED) {
            throw new IllegalStateException("결제 준비 상태에서만 checkout을 확정할 수 있습니다.");
        }
        if (payment == null) {
            throw new IllegalArgumentException("확정할 결제 정보가 필요합니다.");
        }

        this.payment = payment;
        this.status = CheckoutAttemptStatus.CONFIRMED;
    }

    public boolean isConfirmed() {
        return status == CheckoutAttemptStatus.CONFIRMED;
    }

    public boolean isPrepared() {
        return status == CheckoutAttemptStatus.PREPARED;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }
}
