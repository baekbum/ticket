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
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long outboxId;

    @Column(name = "payment_no", nullable = false, length = 60)
    private String paymentNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private VirtualAccountDepositOutboxStatus status = VirtualAccountDepositOutboxStatus.PENDING;

    // 최초 입금 정보를 보존하여 모든 재전달에서 같은 요청을 보낸다.
    @Enumerated(EnumType.STRING)
    @Column(name = "bank_company", nullable = false, length = 30)
    private BankCompany bankCompany;
    @Column(name = "bank_name", nullable = false, length = 50)
    private String bankName;
    @Column(name = "account_number", nullable = false, length = 30)
    private String accountNumber;
    @Column(name = "depositor_name", nullable = false, length = 50)
    private String depositorName;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    @Column(name = "deposited_at", nullable = false)
    private LocalDateTime depositedAt;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;
    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;
    @Column(name = "last_error_type", length = 100)
    private String lastErrorType;
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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

    public void delivered(LocalDateTime now) {
        status = VirtualAccountDepositOutboxStatus.DELIVERED;
        deliveredAt = now;
        lastErrorType = null;
    }

    public void failed(String errorType, LocalDateTime nextAttemptAt) {
        retryCount = Math.incrementExact(retryCount);
        lastErrorType = errorType;
        this.nextAttemptAt = nextAttemptAt;
    }
}
