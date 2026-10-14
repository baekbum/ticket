package dev.bum.ticket_service.jpa.payment.refund;

import dev.bum.ticket_service.jpa.payment.payment.Payment;

import jakarta.persistence.*;

import lombok.*;

/** 개별 환불의 원본 결제와 현재 업무 상태를 관리한다. */
@Entity
@Table(name = "payment_refunds")
@Getter
@NoArgsConstructor
public class PaymentRefund {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentRefundId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_snapshot_id", nullable = false, unique = true)
    private PaymentRefundSnapshot snapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentRefundStatus status;

    public PaymentRefund(Payment payment, PaymentRefundSnapshot snapshot) {
        this.payment = payment;
        this.snapshot = snapshot;
        status = PaymentRefundStatus.REQUESTED;
    }

    public void complete() {
        status = PaymentRefundStatus.COMPLETED;
    }
}
