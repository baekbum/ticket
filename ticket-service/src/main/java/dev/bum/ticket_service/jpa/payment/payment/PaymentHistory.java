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
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentHistoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentHistoryType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PaymentStatus beforeStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus afterStatus;

    @Column(nullable = false)
    private int refundedAmountAfter;

    @Column(nullable = false)
    private int cancellationFeeAmountAfter;

    @Column(length = 1000)
    private String reason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    public PaymentHistory(
            Payment payment, PaymentHistoryType type, PaymentStatus before, String reason) {
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
