package dev.bum.ticket_service.jpa.payment.virtualAccount;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "gateway_bank_cancellation_receipts", uniqueConstraints =
        @UniqueConstraint(name = "uk_gateway_bank_cancel_receipt_payment", columnNames = "payment_no"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GatewayBankCancellationReceipt {

    // PG가 고정한 전체 취소 이벤트. 중복 전달에도 같은 결과를 반환한다.
    @Id
    @Column(name = "event_id", length = 100, updatable = false)
    private String eventId;

    // Ticksy 내부의 원결제 번호. 전체 취소 결과는 한 번만 확정한다.
    @Column(name = "payment_no", nullable = false, length = 60, updatable = false)
    private String paymentNo;

    // 최초 성공/거절 통보의 불변 내용. 같은 키의 다른 금액·결과는 거절한다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, Object> payload;

    // 가맹점의 결과 수신 커밋 시각.
    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    // 기존 로컬 취소 결과를 검증한 트랜잭션에서 수신 이력을 고정한다.
    public static GatewayBankCancellationReceipt received(
            String eventId,
            String paymentNo,
            Map<String, Object> payload
    ) {

        GatewayBankCancellationReceipt receipt = new GatewayBankCancellationReceipt();
        receipt.eventId = eventId;
        receipt.paymentNo = paymentNo;
        receipt.payload = new java.util.LinkedHashMap<>(payload);

        return receipt;
    }
}
