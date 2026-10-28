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
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long outboxId;
    @Column(name = "payment_no", nullable = false, updatable = false, length = 60)
    private String paymentNo;
    @Column(name = "bank_name", nullable = false, updatable = false, length = 50)
    private String bankName;
    @Column(name = "account_number", nullable = false, updatable = false, length = 50)
    private String accountNumber;
    @Column(nullable = false, updatable = false)
    private Integer amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private VirtualAccountCancelOutboxStatus status;
    @Column(name = "retry_count", nullable = false)
    private int retryCount;
    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;
    @Column(name = "last_error_type", length = 100)
    private String lastErrorType;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

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

    public GatewayVirtualAccountCancelRequest toRequest() {
        return new GatewayVirtualAccountCancelRequest(paymentNo, bankName, accountNumber, BigDecimal.valueOf(amount));
    }

    public void delivered(LocalDateTime now) {
        status = VirtualAccountCancelOutboxStatus.DELIVERED;
        deliveredAt = now;
        lastErrorType = null;
    }

    public void failed(String errorType, LocalDateTime nextAttemptAt) {
        retryCount = Math.incrementExact(retryCount);
        lastErrorType = errorType;
        this.nextAttemptAt = nextAttemptAt;
    }
}
