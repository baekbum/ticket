package dev.bum.ticket_service.jpa.payment.virtualAccount;

import dev.bum.common.service.ticket.payment.dto.GatewayVirtualAccountCancelRequest;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "virtual_account_cancel_outbox", indexes =
        @Index(name = "idx_va_cancel_outbox_ready", columnList = "status,next_attempt_at,outbox_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_va_cancel_outbox_payment_no", columnNames = "payment_no"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VirtualAccountCancelOutbox {

    // 발행 대기 이벤트 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long outboxId;

    // 결제 번호.
    @Column(name = "payment_no", nullable = false, updatable = false, length = 60)
    private String paymentNo;

    // 은행 이름.
    @Column(name = "bank_name", nullable = false, updatable = false, length = 50)
    private String bankName;

    // 계좌 번호.
    @Column(name = "account_number", nullable = false, updatable = false, length = 50)
    private String accountNumber;

    // 금액.
    @Column(nullable = false, updatable = false)
    private Integer amount;

    // 가상계좌 취소 Outbox 이벤트 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VirtualAccountCancelOutboxStatus status;

    // 재시도 횟수.
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    // 다음 재시도 예정 시각.
    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    // 마지막 오류 유형.
    @Column(name = "last_error_type", length = 100)
    private String lastErrorType;

    // 생성 시각.
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 전달 또는 배송 완료 시각.
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    // 가상계좌 취소 Outbox 이벤트 처리에서 처리 대기 상태로 구성한다.
    public static VirtualAccountCancelOutbox pending(Payment payment) {

        VirtualAccountCancelOutbox outbox = new VirtualAccountCancelOutbox();
        outbox.paymentNo = payment.getPaymentNo();
        outbox.bankName = payment.getBankName();
        outbox.accountNumber = payment.getAccountNumber();
        outbox.amount = payment.getAmount();
        outbox.status = VirtualAccountCancelOutboxStatus.PENDING;
        outbox.createdAt = LocalDateTime.now();
        outbox.nextAttemptAt = outbox.createdAt;
        return outbox;
    }

    // 후속 처리에 사용할 요청 객체로 변환한다.
    public GatewayVirtualAccountCancelRequest toRequest() {

        return new GatewayVirtualAccountCancelRequest(paymentNo, bankName, accountNumber, BigDecimal.valueOf(amount));
    }

    // 가상계좌 취소 Outbox 이벤트 처리에서 전달 완료 상태를 반영한다.
    public void delivered(LocalDateTime now) {

        status = VirtualAccountCancelOutboxStatus.DELIVERED;
        deliveredAt = now;
        lastErrorType = null;
    }

    // 가상계좌 취소 Outbox 이벤트 처리에서 실패 사유와 상태를 반영한다.
    public void failed(String errorType, LocalDateTime nextAttemptAt) {

        retryCount = Math.incrementExact(retryCount);
        lastErrorType = errorType;
        this.nextAttemptAt = nextAttemptAt;
    }
}
