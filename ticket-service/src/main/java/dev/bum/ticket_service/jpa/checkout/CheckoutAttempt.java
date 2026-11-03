package dev.bum.ticket_service.jpa.checkout;

import dev.bum.ticket_service.jpa.payment.payment.Payment;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

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

    // 중복 처리를 방지하는 멱등성 키.
    @Id
    @Column(name = "idempotency_key", length = 100, nullable = false, updatable = false)
    private String idempotencyKey;

    // 사용자 ID.
    @Column(name = "user_id", length = 100, nullable = false, updatable = false)
    private String userId;

    // 주문 식별자.
    @Column(name = "order_id", length = 50, nullable = false, updatable = false)
    private String orderId;

    // 공연 식별자.
    @Column(name = "event_id", nullable = false, updatable = false)
    private Long eventId;

    // 좌석 식별자 목록.
    @ElementCollection
    @CollectionTable(
            name = "checkout_attempt_seats",
            joinColumns = @JoinColumn(name = "idempotency_key"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_checkout_attempt_seats", columnNames = {"idempotency_key", "seat_id"}))
    @Column(name = "seat_id", nullable = false)
    @Builder.Default
    private Set<Long> seatIds = new HashSet<>();

    // 결제 번호.
    @Column(name = "payment_no", length = 60, nullable = false, updatable = false)
    private String paymentNo;

    // 결제 정보.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    // 결제 준비 시도 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CheckoutAttemptStatus status;

    // 만료 시각.
    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    // 생성 시각.
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 마지막 수정 시각.
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 결제 준비 시도 처리에서 처리에 필요한 상태와 데이터를 준비한다.
    public static CheckoutAttempt prepare(
            String idempotencyKey,
            String userId,
            String orderId,
            Long eventId,
            Set<Long> seatIds,
            String paymentNo,
            LocalDateTime expiresAt
    ) {

        if (seatIds == null || seatIds.isEmpty()
                || seatIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("결제 준비에 유효한 좌석 ID 목록이 필요합니다.");
        }
        return CheckoutAttempt.builder()
                .idempotencyKey(idempotencyKey)
                .userId(userId)
                .orderId(orderId)
                .eventId(eventId)
                .seatIds(new HashSet<>(seatIds))
                .paymentNo(paymentNo)
                .status(CheckoutAttemptStatus.PREPARED)
                .expiresAt(expiresAt)
                // 최초 응답과 DB 재조회 응답이 같은 준비 시각을 사용하도록 DB 정밀도에 맞춘다.
                .createdAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS))
                .build();
    }

    // 처리 대상 좌석 식별자 목록을 반환한다.
    public Set<Long> getSeatIds() {

        return Set.copyOf(seatIds);
    }

    // 준비된 요청을 확정 상태로 변경한다.
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

    // 요청이 확정되었는지 확인한다.
    public boolean isConfirmed() {

        return status == CheckoutAttemptStatus.CONFIRMED;
    }

    // 요청 준비가 완료되었는지 확인한다.
    public boolean isPrepared() {

        return status == CheckoutAttemptStatus.PREPARED;
    }

    // 유효 기간이 만료되었는지 확인한다.
    public boolean isExpired(LocalDateTime now) {

        return expiresAt == null || !expiresAt.isAfter(now);
    }
}
