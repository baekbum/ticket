package dev.bum.payment_gateway_service.jpa.virtualAccount;

import dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException;

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
import java.util.Objects;

@Entity
@Table(
        name = "dummy_virtual_accounts",
        indexes = {
                @Index(name = "idx_dummy_virtual_accounts_payment_no", columnList = "payment_no"),
                @Index(name = "idx_dummy_virtual_accounts_account_number", columnList = "account_number"),
                @Index(name = "idx_dummy_virtual_accounts_status", columnList = "status"),
                @Index(name = "idx_dummy_virtual_accounts_expiration", columnList = "status,expires_at,pg_payment_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_dummy_virtual_accounts_pg_payment", columnNames = "pg_payment_id"),
                @UniqueConstraint(name = "uk_dummy_virtual_accounts_payment_no", columnNames = "payment_no"),
                @UniqueConstraint(name = "uk_dummy_virtual_accounts_account_number", columnNames = "account_number")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DummyVirtualAccount {

    // 가상계좌 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "virtual_account_id")
    private Long virtualAccountId;

    // 연결된 PG 거래의 yyyyMMdd + UUID 문자열. 계좌마다 필수이며 입금 이력은 이 계좌를 통해 연결된다.
    @Column(name = "pg_payment_id", nullable = false, length = 44, updatable = false)
    private String pgPaymentId;

    // 은행 내부 원거래 참조값. PG 금융 요청에서는 pgPaymentId를 사용한다.
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

    // 신규 PG 환불의 누적 금융 실행 금액. 최초 입금 금액·시각은 변경하지 않는다.
    @Builder.Default
    @Column(name = "refunded_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

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

    // 계좌 취소 사유.
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

    // PG 거래를 참조하는 계좌를 발급한다. 가맹점·금액·은행 검증은 PG 계층에서 수행한다.
    public static DummyVirtualAccount issue(
            String pgPaymentId,
            String paymentNo,
            BankCompany bankCompany,
            String accountNumber,
            BigDecimal amount,
            LocalDateTime expiresAt
    ) {

        Objects.requireNonNull(pgPaymentId, "가상계좌에 연결할 PG 결제 식별자가 필요합니다.");

        return DummyVirtualAccount.builder().pgPaymentId(pgPaymentId).paymentNo(paymentNo)
                .bankCompany(bankCompany).bankName(bankCompany.getBankName()).accountNumber(accountNumber)
                .amount(amount).status(VirtualAccountPaymentStatus.WAITING_DEPOSIT).expiresAt(expiresAt).build();
    }

    // 가상계좌 처리에서 입금 결과를 반영한다.
    public void deposit(String depositorName, LocalDateTime depositedAt) {

        this.status = VirtualAccountPaymentStatus.DEPOSITED;
        this.depositorName = depositorName;
        this.depositedAt = depositedAt != null ? depositedAt : LocalDateTime.now();
    }

    // 입금 이력이 존재하는지 확인한다.
    public boolean hasDeposit() {

        return status == VirtualAccountPaymentStatus.DEPOSITED;
    }

    // 계좌 잠금 아래 확정한 환불만 누적한다. 취소 계좌의 늦은 입금 반환은 지원하지 않는다.
    public void refundGateway(BigDecimal value) {

        if (!hasDeposit() || value == null || value.signum() <= 0
                || refundedAmount.add(value).compareTo(amount) > 0) {
            throw new GatewayPaymentConflictException("은행 환불 가능 금액을 초과했거나 환불할 수 없는 계좌입니다.");
        }

        refundedAmount = refundedAmount.add(value);
    }

    // 가상계좌를 만료 상태로 변경한다.
    public void expire() {

        this.status = VirtualAccountPaymentStatus.EXPIRED;
    }

    // 가상계좌를 취소 상태로 변경한다.
    public void cancel(String failureReason) {

        this.status = VirtualAccountPaymentStatus.CANCELLED;
        this.failureReason = failureReason;
    }
}
