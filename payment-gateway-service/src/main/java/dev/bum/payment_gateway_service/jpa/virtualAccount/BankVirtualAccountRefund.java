package dev.bum.payment_gateway_service.jpa.virtualAccount;

import dev.bum.common.service.paymentgateway.refund.*;
import dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "bank_virtual_account_refunds", uniqueConstraints = @UniqueConstraint(name = "uk_bank_virtual_account_refund_request", columnNames = {"pg_payment_id", "refund_request_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankVirtualAccountRefund {

    // 환불 처리 기록의 내부 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refund_id")
    private Long refundId;

    // 은행 원거래의 PG 연결 키.
    @Column(name = "pg_payment_id", nullable = false, length = 44, updatable = false)
    private String pgPaymentId;

    // 원거래 안에서 재사용하는 개별 환불 요청 키.
    @Column(name = "refund_request_id", nullable = false, length = 60, updatable = false)
    private String refundRequestId;

    // 최초 고정 금액.
    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    // 원문 계좌를 포함하지 않는 최초 요청 해시.
    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    // REQUESTED·SUCCEEDED·WITHDRAWN. 은행 기록은 확정된 결과만 저장한다.
    @Column(nullable = false, length = 30)
    private String status;

    // 최초 확정 시각. 재전송이나 철회 요청으로 바꾸지 않는다.
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // 최초 요청 조건을 기록한다.
    public static BankVirtualAccountRefund pending(BankRefundCommand request) {

        BankVirtualAccountRefund row = new BankVirtualAccountRefund();
        row.pgPaymentId = request.pgPaymentId();
        row.refundRequestId = request.refundRequestId();
        row.amount = request.amount();
        row.requestHash = request.fingerprint();
        row.status = "REQUESTED";
        return row;
    }

    // 동일 키의 금액·계좌·원거래 변경을 거절한다.
    public void validate(BankRefundCommand request) {

        if (!requestHash.equals(request.fingerprint())) {
            throw new GatewayPaymentConflictException("같은 환불 요청 키의 내용이 다릅니다.");
        }
    }

    // 최초 금융 결과만 고정한다.
    public void complete(String outcome, LocalDateTime at) {

        if (!"SUCCEEDED".equals(outcome) && !"WITHDRAWN".equals(outcome)) {
            throw new IllegalArgumentException("확정되지 않은 환불 결과입니다.");
        }

        if (!"REQUESTED".equals(status)) {
            if (!status.equals(outcome) || !completedAt.equals(at)) {
                throw new GatewayPaymentConflictException("환불 결과가 변경되었습니다.");
            }
            return;
        }
        status = outcome;
        completedAt = at.truncatedTo(ChronoUnit.MICROS);
    }

    // 동일 요청에는 최초 확정 결과를 돌려준다.
    public BankRefundResult result() {

        return new BankRefundResult(pgPaymentId, refundRequestId, amount, status, completedAt, requestHash);
    }
}
