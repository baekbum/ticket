package dev.bum.payment_gateway_service.jpa.gateway;

import dev.bum.common.service.ticket.payment.enums.CardCompany;
import dev.bum.payment_gateway_service.dto.card.FinancialCardApprovalResponse;
import dev.bum.payment_gateway_service.dto.gateway.CardNotificationSnapshot;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "gateway_card_notification_outbox", uniqueConstraints = {
        @UniqueConstraint(name = "uk_gateway_card_notification_event", columnNames = "event_id"),
        @UniqueConstraint(name = "uk_gateway_card_notification_payment", columnNames = "pg_payment_id")
}, indexes = @Index(name = "idx_gateway_card_notification_ready", columnList = "status,next_attempt_at,notification_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GatewayCardNotification {

    // 통보 행 식별자. 이벤트 식별자는 별도로 안정적으로 유지한다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    // 같은 거래/승인 작업의 재시도에서 유지하는 이벤트 ID.
    @Column(name = "event_id", nullable = false, length = 100, updatable = false)
    private String eventId;

    // 대상 PG 거래. 금융 원승인과 통보는 상태를 공유하지 않는다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pg_payment_id", nullable = false, updatable = false)
    private GatewayPayment payment;

    // 원거래 소유 가맹점 스냅샷. 실제 DB에서는 원거래와 복합 FK로도 검증한다.
    @Column(name = "merchant_id", nullable = false, updatable = false)
    private Long merchantId;

    // 통보할 가맹점 내부 결제번호.
    @Column(name = "merchant_payment_no", nullable = false, length = 60, updatable = false)
    private String merchantPaymentNo;

    // 가맹점이 확인한 예매자.
    @Column(name = "buyer_reference", nullable = false, length = 50, updatable = false)
    private String buyerReference;

    // 최초 통보의 DTO 필드 구성·응답 규칙을 나타내는 계약 버전 스냅샷.
    // 같은 이벤트를 재전달할 때 유지한다. 현재는 V1 고정이며 클래스를 동적으로 찾지 않는다.
    @Column(name = "notification_contract", nullable = false, length = 40, updatable = false)
    private String notificationContract;

    // DB에 등록된 대상 키를 소유 가맹점과 함께 보존한다. URL이나 토큰을 요청에서 받지 않는다.
    @Column(name = "notification_target_key", nullable = false, length = 100, updatable = false)
    private String notificationTargetKey;

    // 원승인 식별자.
    @Column(name = "transaction_id", nullable = false, length = 80, updatable = false)
    private String transactionId;

    // 승인 카드사.
    @Enumerated(EnumType.STRING)
    @Column(name = "card_company", nullable = false, length = 30, updatable = false)
    private CardCompany cardCompany;

    // 통보에 허용된 마스킹 카드번호.
    @Column(name = "masked_card_number", nullable = false, length = 30, updatable = false)
    private String maskedCardNumber;

    // 원승인 금액.
    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    // 원승인 시각.
    @Column(name = "approved_at", nullable = false, updatable = false)
    private LocalDateTime approvedAt;

    // PENDING/SENDING/DELIVERED는 통보 상태이며 PG 금융 상태가 아니다.
    @Column(nullable = false, length = 30)
    private String status;

    // 재임대 시 증가하며 늦게 돌아온 이전 HTTP 응답의 상태 덮어쓰기를 막는다.
    @Column(nullable = false)
    private int attempt;

    // 다음 전달을 시도할 시각.
    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    // 프로세스 중단 시 다른 워커가 회수할 임대 만료 시각.
    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    // 통보 결과. 확정 거절은 실제 승인 보상까지 끝나야 REJECTED로 완료한다.
    @Column(length = 30)
    private String outcome;

    // 민감한 예외 메시지 대신 예외 종류만 기록한다.
    @Column(name = "last_error_type", length = 100)
    private String lastErrorType;

    // 통보 확정 시각.
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    // 최초 저장 시각.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 마지막 상태 변경 시각.
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // PG 승인 상태와 같은 트랜잭션에서 통보 이벤트를 한 번 생성한다.
    public static GatewayCardNotification pending(GatewayPayment payment, FinancialCardApprovalResponse card) {

        GatewayCardNotification event = new GatewayCardNotification();
        event.payment = payment;
        event.eventId = payment.getPgPaymentId() + ":APPROVE:INITIAL";
        event.merchantId = payment.getMerchant().getMerchantId();
        event.merchantPaymentNo = payment.getMerchantPaymentNo();
        event.buyerReference = payment.getBuyerReference();
        event.notificationContract = payment.getMerchant().getNotificationContract();
        event.notificationTargetKey = payment.getMerchant().getNotificationTargetKey();
        event.transactionId = card.transactionId();
        event.cardCompany = card.cardCompany();
        event.maskedCardNumber = card.maskedCardNumber();
        event.amount = payment.getAmount();
        event.approvedAt = card.approvedAt();
        event.status = "PENDING";
        event.nextAttemptAt = LocalDateTime.now();

        return event;
    }

    // 짧은 DB 잠금 안에서만 임대를 잡고 HTTP 전에 커밋한다.
    public CardNotificationSnapshot claim(LocalDateTime now) {

        if ("DELIVERED".equals(status) || nextAttemptAt.isAfter(now)
                || ("SENDING".equals(status) && leaseUntil.isAfter(now))) {
            return null;
        }

        status = "SENDING";
        attempt++;
        leaseUntil = now.plusSeconds(60);

        return new CardNotificationSnapshot(notificationId, eventId, payment.getPgPaymentId(), merchantId,
                merchantPaymentNo, buyerReference, notificationContract, notificationTargetKey,
                transactionId, cardCompany, maskedCardNumber, amount, approvedAt, attempt);
    }

    // 현재 전달 시도만 결과를 반영할 수 있다.
    public boolean owns(int expectedAttempt) {

        return "SENDING".equals(status) && attempt == expectedAttempt;
    }

    // HTTP 수신 또는 승인 보상을 확정한 현재 시도를 완료한다.
    public void delivered(String result) {

        status = "DELIVERED";
        outcome = result;
        deliveredAt = LocalDateTime.now();
        leaseUntil = null;
        lastErrorType = null;
    }

    // 금융 결과는 유지한 채 동일 이벤트의 통보만 재시도한다.
    public void failed(String errorType) {

        status = "PENDING";
        nextAttemptAt = LocalDateTime.now().plusSeconds(30);
        leaseUntil = null;
        lastErrorType = errorType.substring(0, Math.min(errorType.length(), 100));
    }
}
