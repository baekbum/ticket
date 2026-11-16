package dev.bum.payment_gateway_service.jpa.gateway;

import dev.bum.common.service.paymentgateway.refund.BankRefundResult;
import dev.bum.common.service.paymentgateway.refund.PaymentRefundResult;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import dev.bum.payment_gateway_service.dto.card.FinancialCardApprovalResponse;
import dev.bum.payment_gateway_service.dto.bank.BankVirtualAccountDepositResponse;
import dev.bum.payment_gateway_service.dto.bank.BankVirtualAccountCancelResponse;
import java.util.LinkedHashMap;
import dev.bum.payment_gateway_service.dto.bank.BankExpirationEvent;

@Entity
@Table(name = "gateway_payment_histories", uniqueConstraints = @UniqueConstraint(
        name = "uk_gateway_payment_histories_operation", columnNames = {"pg_payment_id", "operation_type", "operation_key"}),
        indexes = @Index(name = "idx_gateway_payment_histories_payment_time",
                columnList = "pg_payment_id,occurred_at,history_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GatewayPaymentHistory {

    // 처리 이력 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    // 해당 업무 처리가 속한 PG 거래.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pg_payment_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_gateway_payment_histories_payment"))
    private GatewayPayment payment;

    // 승인·입금·환불 등 작업 종류.
    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 40, updatable = false)
    private GatewayPaymentOperation operationType;

    // 동일 거래와 작업 종류 안에서 사용하는 안정적인 키.
    @Column(name = "operation_key", nullable = false, length = 100, updatable = false)
    private String operationKey;

    // 변경 전 PG 상태. 최초 등록에는 없다.
    @Enumerated(EnumType.STRING)
    @Column(name = "before_status", length = 30, updatable = false)
    private GatewayPaymentStatus beforeStatus;

    // 변경 후 PG 상태.
    @Enumerated(EnumType.STRING)
    @Column(name = "after_status", nullable = false, length = 30, updatable = false)
    private GatewayPaymentStatus afterStatus;

    // 이번 처리의 금액.
    @Column(precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    // 비밀값을 포함하지 않는 최초 요청 해시.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", length = 64, updatable = false)
    private String requestHash;

    // 명시적으로 허용한 처리 상세만 저장한다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, Object> details;

    // 업무 처리가 확정된 시각.
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    // 전환의 최초 조건과 결과는 별개 작업 키로 고정한다. 최초 prepare 해시는 바꾸지 않는다.
    public static GatewayPaymentHistory methodChange(
            GatewayPayment payment,
            GatewayPaymentTerms target,
            String operationKey
    ) {

        GatewayPaymentHistory history = new GatewayPaymentHistory();
        history.payment = payment;
        history.operationType = GatewayPaymentOperation.CHANGE_METHOD;
        history.operationKey = operationKey;
        history.beforeStatus = payment.getStatus();
        history.afterStatus = payment.getStatus();
        history.amount = payment.getAmount();
        history.requestHash = target.fingerprint();
        history.details = Map.of("bankCompany", target.bankCompany().name(), "expiresAt", target.expiresAt().toString(),
                "sourceExpiresAt", payment.getExpiresAt().toString(), "methodRevision", payment.getMethodRevision());
        history.occurredAt = LocalDateTime.now();

        return history;
    }

    // 이력 저장 시각.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 거래 준비와 같은 트랜잭션에서 최초 이력을 생성한다.
    public static GatewayPaymentHistory registered(GatewayPayment payment) {

        GatewayPaymentHistory history = new GatewayPaymentHistory();
        history.payment = payment;
        history.operationType = GatewayPaymentOperation.REGISTER;
        history.operationKey = "INITIAL";
        history.afterStatus = payment.getStatus();
        history.amount = payment.getAmount();
        history.requestHash = payment.getCreationRequestHash();
        history.details = Map.of();
        history.occurredAt = LocalDateTime.now();

        return history;
    }

    // 승인 결과와 PG 완료 상태·통보 Outbox를 같은 PG 트랜잭션으로 기록한다.
    public static GatewayPaymentHistory cardApproved(
            GatewayPayment payment,
            FinancialCardApprovalResponse card,
            String requestHash
    ) {

        GatewayPaymentHistory history = new GatewayPaymentHistory();
        history.payment = payment;
        history.operationType = GatewayPaymentOperation.APPROVE;
        history.operationKey = "INITIAL";
        history.beforeStatus = GatewayPaymentStatus.READY;
        history.afterStatus = payment.getStatus();
        history.amount = payment.getAmount();
        history.requestHash = requestHash;
        history.details = Map.of("transactionId", card.transactionId(), "cardCompany", card.cardCompany().name(),
                "maskedCardNumber", card.maskedCardNumber(), "approvedAt", card.approvedAt().toString());
        history.occurredAt = LocalDateTime.now();

        return history;
    }

    // 가맹점의 확정 거절에 대해 실제 승인 보상이 끝난 사실을 한 번 기록한다.
    public static GatewayPaymentHistory cardApprovalCancelled(GatewayPayment payment, String transactionId) {

        GatewayPaymentHistory history = new GatewayPaymentHistory();
        history.payment = payment;
        history.operationType = GatewayPaymentOperation.CANCEL_APPROVAL;
        history.operationKey = "INITIAL";
        history.beforeStatus = GatewayPaymentStatus.PAID;
        history.afterStatus = payment.getStatus();
        history.amount = payment.getAmount();
        history.requestHash = payment.getCreationRequestHash();
        history.details = Map.of("transactionId", transactionId, "reason", "MERCHANT_PAYMENT_REJECTED");
        history.occurredAt = LocalDateTime.now();

        return history;
    }

    // 최초 입금 사실과 PG 상태·통보 Outbox를 같은 트랜잭션에 기록한다.
    public static GatewayPaymentHistory deposited(
            GatewayPayment payment,
            BankVirtualAccountDepositResponse bank,
            GatewayPaymentStatus beforeStatus
    ) {

        GatewayPaymentHistory history = new GatewayPaymentHistory();
        history.payment = payment;
        history.operationType = GatewayPaymentOperation.DEPOSIT;
        history.operationKey = "INITIAL";
        history.beforeStatus = beforeStatus;
        history.afterStatus = payment.getStatus();
        history.amount = bank.amount();
        history.requestHash = payment.getCreationRequestHash();
        history.details = Map.of("accountNumber", bank.accountNumber(), "bankCompany", bank.bankCompany().name(),
                "depositorName", bank.depositorName(), "depositedAt", bank.depositedAt().toString());
        history.occurredAt = bank.depositedAt();

        return history;
    }

    // 취소된 예매의 입금 통보를 정리했으며 실제 반환은 하지 않았다는 사실을 기록한다.
    public static GatewayPaymentHistory depositedPaymentCancelled(GatewayPayment payment) {

        GatewayPaymentHistory history = new GatewayPaymentHistory();
        history.payment = payment;
        history.operationType = GatewayPaymentOperation.CANCEL;
        history.operationKey = "MERCHANT_REJECTED_DEPOSIT";
        history.beforeStatus = GatewayPaymentStatus.PAID;
        history.afterStatus = payment.getStatus();
        history.amount = payment.getAmount();
        history.requestHash = payment.getCreationRequestHash();
        history.details = Map.of("reason", "RESERVATION_CANCELLED", "fundsReturned", false);
        history.occurredAt = LocalDateTime.now();

        return history;
    }

    // 가맹점의 전체 계좌 취소 작업을 거래·CANCEL·INITIAL 범위에서 한 번 기록한다.
    public static GatewayPaymentHistory virtualAccountCancelled(
            GatewayPayment payment,
            BankVirtualAccountCancelResponse bank,
            GatewayPaymentStatus beforeStatus
    ) {

        GatewayPaymentHistory history = new GatewayPaymentHistory();
        history.payment = payment;
        history.operationType = GatewayPaymentOperation.CANCEL;
        history.operationKey = "INITIAL";
        history.beforeStatus = beforeStatus;
        history.afterStatus = payment.getStatus();
        history.amount = payment.getAmount();
        history.requestHash = payment.getCreationRequestHash();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("accountNumber", bank.accountNumber());
        snapshot.put("bankName", bank.bankName());
        snapshot.put("bankCompany", bank.bankCompany().name());
        snapshot.put("expiresAt", bank.expiresAt().toString());
        snapshot.put("fundsReturned", false);

        if (bank.depositedAt() != null) {
            snapshot.put("depositedAt", bank.depositedAt().toString());
            snapshot.put("depositorName", bank.depositorName());
        }

        history.details = Map.copyOf(snapshot);
        history.occurredAt = payment.getCancelledAt();

        return history;
    }

    // 취소 거절은 금융 상태를 바꾸지 않고 업무 결과만 남긴다.
    public static GatewayPaymentHistory virtualAccountCancellationRejected(GatewayPayment payment, String reason) {

        GatewayPaymentHistory history = new GatewayPaymentHistory();
        history.payment = payment;
        history.operationType = GatewayPaymentOperation.CANCEL;
        history.operationKey = "REJECTED_INITIAL";
        history.beforeStatus = payment.getStatus();
        history.afterStatus = payment.getStatus();
        history.amount = payment.getAmount();
        history.requestHash = payment.getCreationRequestHash();
        history.details = Map.of("reason", reason, "fundsReturned", false);
        history.occurredAt = LocalDateTime.now();

        return history;
    }

    // 최초 은행 만료 사실과 PG 상태를 같은 트랜잭션에 기록한다. 가맹점 만료 통보는 만들지 않는다.
    public static GatewayPaymentHistory virtualAccountExpired(
            GatewayPayment payment,
            BankExpirationEvent event,
            GatewayPaymentStatus beforeStatus
    ) {

        GatewayPaymentHistory history = new GatewayPaymentHistory();
        history.payment = payment;
        history.operationType = GatewayPaymentOperation.EXPIRE;
        history.operationKey = "INITIAL";
        history.beforeStatus = beforeStatus;
        history.afterStatus = payment.getStatus();
        history.amount = payment.getAmount();
        history.requestHash = payment.getCreationRequestHash();
        history.details = Map.of("eventId", event.eventId(), "accountNumber", event.accountNumber(),
                "bankCompany", event.bankCompany().name(), "expiresAt", event.expiresAt().toString(),
                "expiredAt", event.expiredAt().toString());
        history.occurredAt = event.expiredAt();

        return history;
    }

    // 개별 환불 성공 또는 미실행 철회 사실을 PG 결과와 함께 한 번 기록한다.
    public static GatewayPaymentHistory bankRefund(
            GatewayPayment payment,
            BankRefundResult result,
            GatewayPaymentStatus before
    ) {

        return refund(payment, PaymentRefundResult.from(result), before);
    }

    private static GatewayPaymentHistory refund(
            GatewayPayment payment,
            PaymentRefundResult result,
            GatewayPaymentStatus before
    ) {

        GatewayPaymentHistory history = new GatewayPaymentHistory();
        history.payment = payment;
        history.operationType = GatewayPaymentOperation.REFUND;
        history.operationKey = ("WITHDRAWN".equals(result.outcome()) ? "WITHDRAW:" : "") + result.refundRequestId();
        history.beforeStatus = before;
        history.afterStatus = payment.getStatus();
        history.amount = result.amount();
        history.requestHash = result.requestHash();
        history.details = Map.of("refundRequestId", result.refundRequestId(), "outcome", result.outcome());
        history.occurredAt = result.completedAt();

        return history;
    }

    // 카드 개별 환불 또는 미실행 철회를 원승인 보상 취소와 구분해 기록한다.
    public static GatewayPaymentHistory cardRefund(
            GatewayPayment payment,
            PaymentRefundResult result,
            GatewayPaymentStatus before
    ) {

        return refund(payment, result, before);
    }

    // 금융 응답과 PG 상태가 확정된 트랜잭션에서 최초 발급 결과를 한 번 기록한다.
    public static GatewayPaymentHistory accountIssued(
            GatewayPayment payment,
            String accountNumber,
            String bankName,
            String bankStatus,
            GatewayPaymentStatus beforeStatus
    ) {

        GatewayPaymentHistory history = new GatewayPaymentHistory();
        history.payment = payment;
        history.operationType = GatewayPaymentOperation.ISSUE_ACCOUNT;
        history.operationKey = "INITIAL";
        history.beforeStatus = beforeStatus;
        history.afterStatus = payment.getStatus();
        history.amount = payment.getAmount();
        history.requestHash = payment.getCreationRequestHash();
        history.details = Map.of("accountNumber", accountNumber, "bankName", bankName, "bankStatus", bankStatus);
        history.occurredAt = LocalDateTime.now();

        return history;
    }
}
