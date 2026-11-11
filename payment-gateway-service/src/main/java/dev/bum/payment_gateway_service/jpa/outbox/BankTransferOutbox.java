package dev.bum.payment_gateway_service.jpa.outbox;

import dev.bum.payment_gateway_service.dto.bank.BankTransferDelivery;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "bank_transfer_outbox", uniqueConstraints = {
        @UniqueConstraint(name = "uk_bank_transfer_event", columnNames = {"flow", "event_id"}),
        @UniqueConstraint(name = "uk_bank_transfer_operation", columnNames = {"flow", "pg_payment_id"})
}, indexes = @Index(name = "idx_bank_transfer_ready", columnList = "flow,status,next_attempt_at,outbox_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankTransferOutbox {

    // 서로 다른 전달 단계는 같은 이벤트 ID여도 별도 Outbox 행으로 관리한다.
    public enum Flow { BANK_DEPOSIT, BANK_CANCEL_REQUEST, MERCHANT_CANCEL_RESULT }

    // 전달 행의 내부 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long outboxId;

    // 은행 입금 통보, PG의 은행 취소 요청, PG의 가맹점 취소 통보를 구분한다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private Flow flow;

    // 거래·작업별로 고정하며 재시도할 때 새로 생성하지 않는다.
    @Column(name = "event_id", nullable = false, length = 100, updatable = false)
    private String eventId;

    // 가맹점 결제번호가 중복돼도 충돌하지 않는 PG 거래 참조.
    @Column(name = "pg_payment_id", nullable = false, length = 44, updatable = false)
    private String pgPaymentId;

    // 최초 요청의 비밀값 없는 불변 스냅샷. URL과 인증 토큰은 넣지 않는다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, Object> payload;

    // 수신자가 확정한 업무 결과. 전달 성공과 업무 성공을 구분한다.
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> result;

    // PENDING/SENDING/DELIVERED는 전달 상태이며 금융 상태가 아니다.
    @Column(nullable = false, length = 30)
    private String status;

    // 임대 소유권을 판별하는 전달 시도 번호.
    @Column(nullable = false)
    private int attempt;

    // 실패 후 같은 이벤트를 재전달할 시각.
    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    // 워커 종료 후 다른 워커가 회수할 수 있는 임대 기한.
    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    // 민감한 HTTP 본문 대신 실패 예외의 종류만 기록한다.
    @Column(name = "last_error_type", length = 100)
    private String lastErrorType;

    // 수신 결과를 로컬 DB에 확정한 시각.
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    // 최초 저장 시각.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 마지막 전달 상태 갱신 시각.
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 도메인 변경과 같은 트랜잭션에서 보낼 내용을 고정한다.
    public static BankTransferOutbox pending(
            Flow flow,
            String eventId,
            String pgPaymentId,
            Map<String, Object> payload
    ) {

        BankTransferOutbox event = new BankTransferOutbox();
        event.flow = flow;
        event.eventId = eventId;
        event.pgPaymentId = pgPaymentId;
        event.payload = new java.util.LinkedHashMap<>(payload);
        event.status = "PENDING";
        event.nextAttemptAt = LocalDateTime.now();

        return event;
    }

    // 짧은 잠금 안에서 임대를 잡고 HTTP 전에는 트랜잭션을 끝낸다.
    public BankTransferDelivery claim(LocalDateTime now) {

        if ("DELIVERED".equals(status) || ("PENDING".equals(status) && nextAttemptAt.isAfter(now))
                || ("SENDING".equals(status) && leaseUntil.isAfter(now))) {
            return null;
        }

        status = "SENDING";
        attempt++;
        leaseUntil = now.plusSeconds(60);

        return new BankTransferDelivery(outboxId, flow, eventId, pgPaymentId, payload, attempt);
    }

    // 지연된 이전 워커가 최신 결과를 덮어쓰지 못하게 한다.
    public boolean owns(BankTransferDelivery delivery) {

        return outboxId.equals(delivery.outboxId()) && flow == delivery.flow()
                && "SENDING".equals(status) && attempt == delivery.attempt();
    }

    // 업무 거절도 확정 결과라면 전달을 완료하고 원래 결과를 보관한다.
    public void delivered(Map<String, Object> result) {

        this.result = new java.util.LinkedHashMap<>(result);
        status = "DELIVERED";
        deliveredAt = LocalDateTime.now();
        leaseUntil = null;
        lastErrorType = null;
    }

    // 상태 변경이나 응답 저장 실패는 같은 키와 내용으로 다시 시도한다.
    public void failed(String errorType) {

        status = "PENDING";
        nextAttemptAt = LocalDateTime.now().plusSeconds(30);
        leaseUntil = null;
        lastErrorType = errorType.substring(0, Math.min(100, errorType.length()));
    }
}
