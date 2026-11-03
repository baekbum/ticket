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

    // 결제 처리 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentProcessId;

    // 작업 진행 단계.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentProcessStage stage;

    // 실패 사유.
    @Column(length = 1000)
    private String failureReason;

    // 재시도 횟수.
    @Column(nullable = false)
    private int retryCount;

    // 마지막 시도 시각.
    @Column(nullable = false)
    private LocalDateTime lastTriedAt;

    // 전달받은 값으로 결제 객체를 초기화한다.
    public PaymentProcess(PaymentProcessStage stage) {

        this.stage = stage;
        this.lastTriedAt = LocalDateTime.now();
    }

    // 결제 처리에서 연결된 위치 또는 대상을 변경한다.
    public void move(PaymentProcessStage stage, String reason) {

        if (this.stage == PaymentProcessStage.FAILED
                && stage == PaymentProcessStage.READY_FOR_APPROVAL) retryCount++;
        this.stage = stage;
        this.failureReason =
                reason == null ? null : reason.substring(0, Math.min(1000, reason.length()));
        this.lastTriedAt = LocalDateTime.now();
    }
}
