package dev.bum.ticket_service.jpa.payment.payment;

import jakarta.persistence.*;

import lombok.*;

import java.time.LocalDateTime;

/** 결제 작업의 최신 단계와 실패 정보를 관리한다. 결제 금액과 업무 상태는 저장하지 않는다. */
@Entity
@Table(name = "payment_processes")
@Getter
@NoArgsConstructor
public class PaymentProcess {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentProcessId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentProcessStage stage;

    @Column(length = 1000)
    private String failureReason;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private LocalDateTime lastTriedAt;

    public PaymentProcess(PaymentProcessStage stage) {
        this.stage = stage;
        this.lastTriedAt = LocalDateTime.now();
    }

    public void move(PaymentProcessStage stage, String reason) {
        if (this.stage == PaymentProcessStage.FAILED
                && stage == PaymentProcessStage.READY_FOR_APPROVAL) retryCount++;
        this.stage = stage;
        this.failureReason =
                reason == null ? null : reason.substring(0, Math.min(1000, reason.length()));
        this.lastTriedAt = LocalDateTime.now();
    }
}
