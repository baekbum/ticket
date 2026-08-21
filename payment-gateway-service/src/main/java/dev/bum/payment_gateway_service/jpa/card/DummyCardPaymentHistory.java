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

@Entity
@Table(
        name = "dummy_card_payment_histories",
        indexes = {
                @Index(name = "idx_dummy_card_payment_histories_user_id", columnList = "user_id"),
                @Index(name = "idx_dummy_card_payment_histories_payment_no", columnList = "payment_no"),
                @Index(name = "idx_dummy_card_payment_histories_status", columnList = "status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_dummy_card_payment_histories_payment_no", columnNames = "payment_no")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DummyCardPaymentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    // 결제 승인에 사용된 더미 카드. 카드 조회 실패 이력은 null로 저장된다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dummy_card_id")
    private DummyCard dummyCard;

    // 결제 요청 사용자 ID.
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    // ticket-service에서 생성한 결제 식별 번호.
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

    // payment-gateway와 ticket-service 연동 처리 상태.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private CardPaymentHistoryStatus status;

    // 카드 승인 실패 또는 ticket-service 결제 완료 요청 실패 사유.
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    // 카드 검증과 승인 처리가 처리된 시각.
    @Column(name = "approved_at", nullable = false)
    private LocalDateTime approvedAt;

    // ticket-service 결제 완료 반영 시각.
    @Column(name = "ticket_completed_at")
    private LocalDateTime ticketCompletedAt;

    // 카드 사용 내역 row가 DB에 최초 저장된 시각.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 카드 사용 내역 상태가 마지막으로 변경된 시각.
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static DummyCardPaymentHistory approved(
            DummyCard dummyCard,
            String paymentNo,
            String transactionId,
            String maskedCardNumber,
            BigDecimal amount
    ) {
        return DummyCardPaymentHistory.builder()
                .dummyCard(dummyCard)
                .userId(dummyCard.getUserId())
                .paymentNo(paymentNo)
                .transactionId(transactionId)
                .cardCompany(dummyCard.getCardCompany())
                .maskedCardNumber(maskedCardNumber)
                .amount(amount)
                .status(CardPaymentHistoryStatus.APPROVED)
                .approvedAt(LocalDateTime.now())
                .build();
    }

    public static DummyCardPaymentHistory approvalFailed(
            DummyCard dummyCard,
            String userId,
            String paymentNo,
            CardCompany cardCompany,
            String maskedCardNumber,
            BigDecimal amount,
            String failureReason
    ) {
        return DummyCardPaymentHistory.builder()
                .dummyCard(dummyCard)
                .userId(userId)
                .paymentNo(paymentNo)
                .cardCompany(cardCompany)
                .maskedCardNumber(maskedCardNumber)
                .amount(amount)
                .status(CardPaymentHistoryStatus.APPROVAL_FAILED)
                .failureReason(failureReason)
                .approvedAt(LocalDateTime.now())
                .build();
    }

    public void completeTicketPayment(LocalDateTime ticketCompletedAt) {
        this.status = CardPaymentHistoryStatus.TICKET_PAYMENT_COMPLETED;
        this.ticketCompletedAt = ticketCompletedAt != null ? ticketCompletedAt : LocalDateTime.now();
        this.failureReason = null;
    }

    public void failTicketPayment(String failureReason) {
        this.status = CardPaymentHistoryStatus.TICKET_PAYMENT_FAILED;
        this.failureReason = failureReason;
    }

    public void cancel(String failureReason) {
        this.status = CardPaymentHistoryStatus.CANCELLED;
        this.failureReason = failureReason;
    }

    public void refund() {
        this.status = CardPaymentHistoryStatus.REFUNDED;
        this.failureReason = null;
    }
}
