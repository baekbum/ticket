package dev.bum.common.service.paymentgateway.refund;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentRefundResult(
        // 저장된 PG 원거래 식별자.
        String pgPaymentId,

        // 최초 환불 신청 키.
        String refundRequestId,

        // 최초 확정 환불 금액.
        BigDecimal amount,

        // SUCCEEDED 또는 WITHDRAWN. 통신 실패는 금융 성공으로 기록하지 않는다.
        String outcome,

        // 재요청에서도 유지하는 최초 금융 확정 시각.
        LocalDateTime completedAt,

        // 결제수단별 최초 금융 요청 해시.
        String requestHash
) {

    public static PaymentRefundResult from(BankRefundResult result) {

        return new PaymentRefundResult(result.pgPaymentId(), result.refundRequestId(), result.amount(),
                result.outcome(), result.completedAt(), result.requestHash());
    }

    public boolean matches(BankRefundCommand request) {

        return new BankRefundResult(pgPaymentId, refundRequestId, amount, outcome, completedAt, requestHash).matches(request);
    }

    public boolean matches(CardRefundCommand request) {

        return request.pgPaymentId().equals(pgPaymentId) && request.refundRequestId().equals(refundRequestId)
                && amount != null && request.amount().compareTo(amount) == 0 && completedAt != null
                && ("SUCCEEDED".equals(outcome) || "WITHDRAWN".equals(outcome))
                && request.fingerprint().equals(requestHash);
    }
}
