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

    // 결제 승인에 사용된 더미 카드.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dummy_card_id", nullable = false)
    private DummyCard dummyCard;

    // 결제 요청 사용자 ID.
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    // ticket-service에서 생성한 결제 식별 번호.
    @Column(name = "payment_no", nullable = false, length = 60)
    private String paymentNo;

    // 승인 당시 카드사 스냅샷.
    @Enumerated(EnumType.STRING)
    @Column(name = "card_company", nullable = false, length = 30)
    private CardCompany cardCompany;

    // 승인 당시 카드번호 마지막 4자리 스냅샷.
    @Column(name = "card_number_last4", nullable = false, length = 4)
    private String cardNumberLast4;

    // 승인 금액.
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    // payment-gateway와 ticket-service 연동 처리 상태.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private CardPaymentHistoryStatus status;

    // ticket-service 결제 완료 요청 실패 사유.
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    // 카드 검증과 승인 처리가 완료된 시각.
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

    public static DummyCardPaymentHistory approved(DummyCard dummyCard, String paymentNo, BigDecimal amount) {
        return DummyCardPaymentHistory.builder()
                .dummyCard(dummyCard)
                .userId(dummyCard.getUserId())
                .paymentNo(paymentNo)
                .cardCompany(dummyCard.getCardCompany())
                .cardNumberLast4(dummyCard.getCardNumberLast4())
                .amount(amount)
                .status(CardPaymentHistoryStatus.APPROVED)
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
}
