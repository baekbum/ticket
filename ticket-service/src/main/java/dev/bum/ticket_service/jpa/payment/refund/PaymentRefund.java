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

    // 결제 환불 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentRefundId;

    // 결제 정보.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    // 처리 시점의 데이터 스냅샷.
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_snapshot_id", nullable = false, unique = true)
    private PaymentRefundSnapshot snapshot;

    // 결제 환불 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentRefundStatus status;

    // 전달받은 값으로 결제 환불 객체를 초기화한다.
    @Builder
    public PaymentRefund(Payment payment, PaymentRefundSnapshot snapshot) {

        this.payment = payment;
        this.snapshot = snapshot;
        status = PaymentRefundStatus.REQUESTED;
    }

    // 결제 환불 처리에서 완료 상태로 변경하고 결과를 반영한다.
    public void complete() {

        status = PaymentRefundStatus.COMPLETED;
    }

    // 기존 요청을 새 요청으로 대체된 상태로 변경한다.
    public void supersede() {

        status = PaymentRefundStatus.SUPERSEDED;
    }
}
