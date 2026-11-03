package dev.bum.payment_gateway_service.jpa.outbox;

import dev.bum.common.service.ticket.payment.dto.VirtualAccountDepositCompleteRequest;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccount;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "virtual_account_deposit_outbox", indexes =
        @Index(name = "idx_va_deposit_outbox_ready", columnList = "status,next_attempt_at,outbox_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_va_deposit_outbox_payment_no", columnNames = "payment_no"))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualAccountDepositOutbox {

    // 발행 대기 이벤트 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long outboxId;

    // 결제 번호.
    @Column(name = "payment_no", nullable = false, length = 60)
    private String paymentNo;

    // 가상계좌 입금 Outbox 이벤트 상태.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private VirtualAccountDepositOutboxStatus status = VirtualAccountDepositOutboxStatus.PENDING;

    // 최초 입금 정보를 보존하여 모든 재전달에서 같은 요청을 보낸다.
    @Enumerated(EnumType.STRING)
    @Column(name = "bank_company", nullable = false, length = 30)
    private BankCompany bankCompany;

    // 은행 이름.
    @Column(name = "bank_name", nullable = false, length = 50)
    private String bankName;

    // 계좌 번호.
    @Column(name = "account_number", nullable = false, length = 30)
    private String accountNumber;

    // 입금자 이름.
    @Column(name = "depositor_name", nullable = false, length = 50)
    private String depositorName;

    // 금액.
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    // 입금 완료 시각.
    @Column(name = "deposited_at", nullable = false)
    private LocalDateTime depositedAt;

    // 재시도 횟수.
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    // 다음 재시도 예정 시각.
    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    // 마지막 오류 유형.
    @Column(name = "last_error_type", length = 100)
    private String lastErrorType;

    // 전달 또는 배송 완료 시각.
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    // 생성 시각.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 마지막 수정 시각.
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 가상계좌 입금 Outbox 이벤트 처리에서 처리 대기 상태로 구성한다.
    public static VirtualAccountDepositOutbox pending(DummyVirtualAccount account) {

        return VirtualAccountDepositOutbox.builder()
                .paymentNo(account.getPaymentNo())
                .bankCompany(account.getBankCompany())
                .bankName(account.getBankName())
                .accountNumber(account.getAccountNumber())
                .depositorName(account.getDepositorName())
                .amount(account.getAmount())
                .depositedAt(account.getDepositedAt())
                .nextAttemptAt(LocalDateTime.now())
                .build();
    }

    // 후속 처리에 사용할 요청 객체로 변환한다.
    public VirtualAccountDepositCompleteRequest toRequest() {

        return VirtualAccountDepositCompleteRequest.builder()
                .paymentNo(paymentNo)
                .bankCompany(bankCompany)
                .bankName(bankName)
                .accountNumber(accountNumber)
                .depositorName(depositorName)
                .amount(amount)
                .depositedAt(depositedAt)
                .build();
    }

    // 가상계좌 입금 Outbox 이벤트 처리에서 전달 완료 상태를 반영한다.
    public void delivered(LocalDateTime now) {

        status = VirtualAccountDepositOutboxStatus.DELIVERED;
        deliveredAt = now;
        lastErrorType = null;
    }

    // 가상계좌 입금 Outbox 이벤트 처리에서 실패 사유와 상태를 반영한다.
    public void failed(String errorType, LocalDateTime nextAttemptAt) {

        retryCount = Math.incrementExact(retryCount);
        lastErrorType = errorType;
        this.nextAttemptAt = nextAttemptAt;
    }
}
