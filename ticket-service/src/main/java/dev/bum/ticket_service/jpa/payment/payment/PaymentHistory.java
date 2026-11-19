package dev.bum.ticket_service.jpa.payment.payment;

import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;

import jakarta.persistence.*;

import lombok.*;

import java.time.LocalDateTime;

/** 결제에 발생한 사건을 누적한다. 현재 결제 정보나 계산 근거를 중복 저장하지 않는다. */
@Entity
@Table(
        name = "payment_histories",
        indexes = @Index(name = "idx_payment_histories_payment", columnList = "payment_id"))
@Getter
@NoArgsConstructor
public class PaymentHistory {

    // 결제 이력 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentHistoryId;

    // 결제 정보.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    // 이벤트 종류.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentHistoryType eventType;

    // 결제 수단.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod method;

    // 변경 전 상태.
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PaymentStatus beforeStatus;

    // 변경 후 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus afterStatus;

    // 처리 후 누적 환불 금액.
    @Column(nullable = false)
    private int refundedAmountAfter;

    // 처리 후 누적 취소 수수료.
    @Column(nullable = false)
    private int cancellationFeeAmountAfter;

    // 처리 사유.
    @Column(length = 1000)
    private String reason;

    // 이벤트 발생 시각.
    @Column(nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    // 전달받은 값으로 결제 객체를 초기화한다.
    public PaymentHistory(
            Payment payment,
            PaymentHistoryType type,
            PaymentStatus before,
            String reason
    ) {

        this.payment = payment;
        eventType = type;
        method = payment.getMethod();
        beforeStatus = before;
        afterStatus = payment.getStatus();
        refundedAmountAfter = payment.getRefundedAmount();
        cancellationFeeAmountAfter = payment.getCancellationFeeAmount();
        this.reason = reason == null ? null : reason.substring(0, Math.min(1000, reason.length()));
        occurredAt = LocalDateTime.now();
    }
}
