package dev.bum.payment_gateway_service.jpa.virtualAccount;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
        name = "dummy_virtual_accounts",
        indexes = {
                @Index(name = "idx_dummy_virtual_accounts_payment_no", columnList = "payment_no"),
                @Index(name = "idx_dummy_virtual_accounts_account_number", columnList = "account_number"),
                @Index(name = "idx_dummy_virtual_accounts_status", columnList = "status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_dummy_virtual_accounts_payment_no", columnNames = "payment_no"),
                @UniqueConstraint(name = "uk_dummy_virtual_accounts_account_number", columnNames = "account_number")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DummyVirtualAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "virtual_account_id")
    private Long virtualAccountId;

    // ticket-service에서 생성한 결제 식별 번호.
    @Column(name = "payment_no", nullable = false, length = 60)
    private String paymentNo;

    // 사용자가 선택한 은행사.
    @Enumerated(EnumType.STRING)
    @Column(name = "bank_company", nullable = false, length = 30)
    private BankCompany bankCompany;

    // 화면에 표시할 은행명 스냅샷.
    @Column(name = "bank_name", nullable = false, length = 50)
    private String bankName;

    // 발급된 더미 가상계좌 번호.
    @Column(name = "account_number", nullable = false, length = 30)
    private String accountNumber;

    // 실제 입금 확인 시점에 저장할 입금자명.
    @Column(name = "depositor_name", length = 50)
    private String depositorName;

    // 입금해야 할 금액.
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    // 가상계좌 입금 처리 상태.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private VirtualAccountPaymentStatus status;

    // 입금 만료 시각.
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // 실제 입금 확인 시각.
    @Column(name = "deposited_at")
    private LocalDateTime depositedAt;

    // ticket-service 결제 완료 반영 시각.
    @Column(name = "ticket_completed_at")
    private LocalDateTime ticketCompletedAt;

    // ticket-service 결제 완료 요청 실패 사유.
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    // 가상계좌 row가 DB에 최초 저장된 시각.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 가상계좌 상태가 마지막으로 변경된 시각.
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static DummyVirtualAccount issue(
            String paymentNo,
            BankCompany bankCompany,
            String accountNumber,
            BigDecimal amount,
            LocalDateTime expiresAt
    ) {
        return DummyVirtualAccount.builder()
                .paymentNo(paymentNo)
                .bankCompany(bankCompany)
                .bankName(bankCompany.getBankName())
                .accountNumber(accountNumber)
                .amount(amount)
                .status(VirtualAccountPaymentStatus.WAITING_DEPOSIT)
                .expiresAt(expiresAt)
                .build();
    }

    public void deposit(String depositorName, LocalDateTime depositedAt) {
        this.status = VirtualAccountPaymentStatus.DEPOSITED;
        this.depositorName = depositorName;
        this.depositedAt = depositedAt != null ? depositedAt : LocalDateTime.now();
    }

    public void publishDepositEvent() {
        this.status = VirtualAccountPaymentStatus.DEPOSIT_EVENT_PUBLISHED;
    }

    public void completeTicketPayment(LocalDateTime ticketCompletedAt) {
        this.status = VirtualAccountPaymentStatus.TICKET_PAYMENT_COMPLETED;
        this.ticketCompletedAt = ticketCompletedAt != null ? ticketCompletedAt : LocalDateTime.now();
        this.failureReason = null;
    }

    public void failTicketPayment(String failureReason) {
        this.status = VirtualAccountPaymentStatus.TICKET_PAYMENT_FAILED;
        this.failureReason = failureReason;
    }

    public void expire() {
        this.status = VirtualAccountPaymentStatus.EXPIRED;
    }

    public void cancel(String failureReason) {
        this.status = VirtualAccountPaymentStatus.CANCELLED;
        this.failureReason = failureReason;
    }
}
