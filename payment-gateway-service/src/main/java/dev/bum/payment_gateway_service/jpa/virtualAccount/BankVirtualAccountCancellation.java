package dev.bum.payment_gateway_service.jpa.virtualAccount;

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
@Table(name = "bank_virtual_account_cancellations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankVirtualAccountCancellation {

    // 전체 취소 INITIAL은 PG 거래마다 한 번만 결정한다. 다른 가맹점의 paymentNo와 충돌하지 않는다.
    @Id
    @Column(name = "pg_payment_id", length = 44, updatable = false)
    private String pgPaymentId;

    // 금액·계좌·은행을 포함한 최초 요청. 거절 후 재시도도 내용이 같아야 한다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", nullable = false, updatable = false)
    private Map<String, Object> requestPayload;

    // 최초 성공 또는 업무 거절 결과. 이후 계좌 상태가 바뀌어도 재응답은 바뀌지 않는다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_payload", nullable = false, updatable = false)
    private Map<String, Object> resultPayload;

    // 은행 업무 결과 확정 시각.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 계좌 상태 변경과 같은 은행 트랜잭션에서 최초 결과를 고정한다.
    public static BankVirtualAccountCancellation completed(
            String pgPaymentId,
            Map<String, Object> request,
            Map<String, Object> result
    ) {

        BankVirtualAccountCancellation receipt = new BankVirtualAccountCancellation();
        receipt.pgPaymentId = pgPaymentId;
        receipt.requestPayload = new java.util.LinkedHashMap<>(request);
        receipt.resultPayload = new java.util.LinkedHashMap<>(result);

        return receipt;
    }
}
