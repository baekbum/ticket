package dev.bum.payment_gateway_service.jpa.virtualAccount;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "dummy_virtual_account_payment_histories",
        indexes = {
                @Index(name = "idx_dummy_virtual_account_histories_payment_no", columnList = "payment_no"),
                @Index(name = "idx_dummy_virtual_account_histories_history_type", columnList = "history_type")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DummyVirtualAccountPaymentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    // 상태 변경이 발생한 더미 가상계좌.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "virtual_account_id", nullable = false)
    private DummyVirtualAccount virtualAccount;

    // ticket-service에서 생성한 결제 식별 번호.
    @Column(name = "payment_no", nullable = false, length = 60)
    private String paymentNo;

    // 가상계좌 상태 변경 이벤트 유형.
    @Enumerated(EnumType.STRING)
    @Column(name = "history_type", nullable = false, length = 40)
    private VirtualAccountPaymentHistoryType historyType;

    // 이력 상세 메시지.
    @Column(name = "message", length = 500)
    private String message;

    // 이력이 DB에 저장된 시각.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static DummyVirtualAccountPaymentHistory issued(DummyVirtualAccount virtualAccount) {
        return DummyVirtualAccountPaymentHistory.builder()
                .virtualAccount(virtualAccount)
                .paymentNo(virtualAccount.getPaymentNo())
                .historyType(VirtualAccountPaymentHistoryType.ISSUED)
                .message("가상계좌가 발급되었습니다.")
                .build();
    }

    public static DummyVirtualAccountPaymentHistory depositEventPublished(DummyVirtualAccount virtualAccount) {
        return DummyVirtualAccountPaymentHistory.builder()
                .virtualAccount(virtualAccount)
                .paymentNo(virtualAccount.getPaymentNo())
                .historyType(VirtualAccountPaymentHistoryType.DEPOSIT_EVENT_PUBLISHED)
                .message("입금 완료 Kafka 이벤트가 발행되었습니다.")
                .build();
    }
}
