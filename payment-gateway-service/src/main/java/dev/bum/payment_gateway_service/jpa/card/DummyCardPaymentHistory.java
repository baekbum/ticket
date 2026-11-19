package dev.bum.payment_gateway_service.jpa.card;

import dev.bum.common.service.ticket.payment.enums.CardCompany;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
        name = "dummy_card_payment_histories",
        indexes = {
                @Index(name = "idx_dummy_card_payment_histories_user_id", columnList = "user_id"),
                @Index(name = "idx_dummy_card_payment_histories_payment_no", columnList = "payment_no"),
                @Index(name = "idx_dummy_card_payment_histories_status", columnList = "status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_dummy_card_payment_histories_pg_payment", columnNames = "pg_payment_id"),
                @UniqueConstraint(name = "uk_dummy_card_payment_histories_payment_no", columnNames = "payment_no")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DummyCardPaymentHistory {

    // 처리 이력 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    // 결제 승인에 사용된 더미 카드. 카드 조회 실패 이력은 null로 저장된다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dummy_card_id")
    private DummyCard dummyCard;

    // 연결된 PG 거래의 yyyyMMdd + UUID 문자열. 카드 승인·실패 거래마다 필수이며 카드 원장과 구분한다.
    @Column(name = "pg_payment_id", nullable = false, length = 44, updatable = false)
    private String pgPaymentId;

    // 로그인한 예매자 ID. 카드 소유자는 연결된 dummyCard에 별도로 보관한다.
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    // 금융 내부 원거래 참조값. 신규 PG 금융 요청에서는 pgPaymentId를 사용한다.
    @Column(name = "payment_no", nullable = false, length = 60)
    private String paymentNo;

    // gateway 카드 승인 거래 식별 번호.
    @Column(name = "transaction_id", length = 80)
    private String transactionId;

    // 요청 당시 카드사 스냅샷.
    @Enumerated(EnumType.STRING)
    @Column(name = "card_company", nullable = false, length = 30)
    private CardCompany cardCompany;

    // 요청 당시 마스킹 카드번호 스냅샷.
    @Column(name = "card_number_masked", nullable = false, length = 30)
    private String maskedCardNumber;

    // 요청 금액.
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    // 누적 환불 금액.
    @Builder.Default
    @Column(name = "refunded_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    // 가맹점 통보 상태와 분리된 카드 금융 처리 상태.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private CardPaymentHistoryStatus status;

    // 카드 금융 승인 실패 또는 취소 사유.
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    // 카드 검증과 승인 처리가 처리된 시각.
    @Column(name = "approved_at", nullable = false)
    private LocalDateTime approvedAt;

    // 카드 사용 내역 row가 DB에 최초 저장된 시각.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 카드 사용 내역 상태가 마지막으로 변경된 시각.
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // PG 거래를 참조하는 카드 승인 거래를 생성한다. 가맹점·금액·수단 검증은 PG 계층에서 수행한다.
    public static DummyCardPaymentHistory approved(
            String pgPaymentId,
            DummyCard dummyCard,
            String requestingUserId,
            String paymentNo,
            String transactionId,
            String maskedCardNumber,
            BigDecimal amount
    ) {

        Objects.requireNonNull(pgPaymentId, "카드 승인에 연결할 PG 결제 식별자가 필요합니다.");

        return DummyCardPaymentHistory.builder().pgPaymentId(pgPaymentId).dummyCard(dummyCard)
                .userId(requestingUserId).paymentNo(paymentNo).transactionId(transactionId)
                .cardCompany(dummyCard.getCardCompany()).maskedCardNumber(maskedCardNumber).amount(amount)
                .status(CardPaymentHistoryStatus.APPROVED)
                .approvedAt(LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS)).build();
    }

    // PG 거래에 속한 최초 승인 실패를 기록한다. 재시도와 재승인은 같은 거래 행을 기준으로 처리한다.
    public static DummyCardPaymentHistory approvalFailed(
            String pgPaymentId,
            DummyCard dummyCard,
            String userId,
            String paymentNo,
            CardCompany cardCompany,
            String maskedCardNumber,
            BigDecimal amount,
            String failureReason
    ) {

        Objects.requireNonNull(pgPaymentId, "카드 승인 실패에 연결할 PG 결제 식별자가 필요합니다.");

        return DummyCardPaymentHistory.builder().pgPaymentId(pgPaymentId).dummyCard(dummyCard)
                .userId(userId).paymentNo(paymentNo).cardCompany(cardCompany).maskedCardNumber(maskedCardNumber)
                .amount(amount).status(CardPaymentHistoryStatus.APPROVAL_FAILED).failureReason(failureReason)
                .approvedAt(LocalDateTime.now()).build();
    }

    // 모의 카드를 취소 상태로 변경한다.
    public void cancel(String failureReason) {

        this.status = CardPaymentHistoryStatus.CANCELLED;
        this.failureReason = failureReason;
    }

    // 모의 카드 처리에서 환불 요청을 처리하고 결과를 반영한다.
    public void refund(BigDecimal refundAmount) {

        validateRefundAmount(refundAmount);
        this.refundedAmount = getRefundedAmount().add(refundAmount);
        this.status = getRefundableAmount().compareTo(BigDecimal.ZERO) == 0
                ? CardPaymentHistoryStatus.REFUNDED
                : CardPaymentHistoryStatus.PARTIALLY_REFUNDED;
        this.failureReason = null;
    }

    // 누적 환불 금액 값을 반환한다.
    public BigDecimal getRefundedAmount() {

        return refundedAmount != null ? refundedAmount : BigDecimal.ZERO;
    }

    // 환불 가능 잔액 값을 반환한다.
    public BigDecimal getRefundableAmount() {

        return amount.subtract(getRefundedAmount());
    }

    // 환불 요청 금액이 환불 가능 범위에 있는지 검증한다.
    private void validateRefundAmount(BigDecimal refundAmount) {

        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("환불 금액은 0보다 커야 합니다.");
        }

        if (refundAmount.compareTo(getRefundableAmount()) > 0) {
            throw new IllegalArgumentException("환불 금액이 남은 카드 승인 금액을 초과했습니다.");
        }
    }
}
