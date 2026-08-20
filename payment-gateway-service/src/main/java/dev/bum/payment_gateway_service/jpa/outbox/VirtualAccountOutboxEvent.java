package dev.bum.payment_gateway_service.jpa.outbox;

import dev.bum.common.kafka.payment.VirtualAccountExpiredEvent;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "virtual_account_outbox_events",
        indexes = {
                @Index(name = "idx_virtual_account_outbox_status_id", columnList = "status,outbox_id"),
                @Index(name = "idx_virtual_account_outbox_payment_no", columnList = "payment_no")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualAccountOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long outboxId;

    // 발행해야 하는 이벤트 유형.
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 60)
    private OutboxEventType eventType;

    // Kafka 발행 상태.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OutboxEventStatus status;

    // ticket-service에서 생성한 결제 식별 번호.
    @Column(name = "payment_no", nullable = false, length = 60)
    private String paymentNo;

    // 이벤트 payload의 은행사.
    @Enumerated(EnumType.STRING)
    @Column(name = "bank_company", nullable = false, length = 30)
    private BankCompany bankCompany;

    // 이벤트 payload의 은행명.
    @Column(name = "bank_name", nullable = false, length = 50)
    private String bankName;

    // 이벤트 payload의 계좌번호.
    @Column(name = "account_number", nullable = false, length = 30)
    private String accountNumber;

    // 이벤트 payload의 결제 금액.
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    // 이벤트가 발생한 시각.
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    // Kafka 발행 재시도 횟수.
    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    // 마지막 Kafka 발행 실패 사유.
    @Column(name = "last_error_message", length = 500)
    private String lastErrorMessage;

    // Kafka 발행 완료 시각.
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    // outbox row가 DB에 최초 저장된 시각.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // outbox row가 마지막으로 변경된 시각.
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static VirtualAccountOutboxEvent virtualAccountExpired(
            DummyVirtualAccount virtualAccount,
            LocalDateTime expiredAt
    ) {
        return VirtualAccountOutboxEvent.builder()
                .eventType(OutboxEventType.VIRTUAL_ACCOUNT_EXPIRED)
                .status(OutboxEventStatus.PENDING)
                .paymentNo(virtualAccount.getPaymentNo())
                .bankCompany(virtualAccount.getBankCompany())
                .bankName(virtualAccount.getBankName())
                .accountNumber(virtualAccount.getAccountNumber())
                .amount(virtualAccount.getAmount())
                .occurredAt(expiredAt)
                .build();
    }

    public VirtualAccountExpiredEvent toVirtualAccountExpiredEvent() {
        return VirtualAccountExpiredEvent.builder()
                .paymentNo(this.paymentNo)
                .bankCompany(this.bankCompany)
                .bankName(this.bankName)
                .accountNumber(this.accountNumber)
                .amount(this.amount)
                .expiredAt(this.occurredAt)
                .build();
    }

    public void publish(LocalDateTime publishedAt) {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = publishedAt != null ? publishedAt : LocalDateTime.now();
        this.lastErrorMessage = null;
    }

    public void failPublish(String errorMessage) {
        this.retryCount = this.retryCount + 1;
        this.lastErrorMessage = errorMessage;
    }
}
