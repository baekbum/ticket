package dev.bum.common.service.paymentgateway.refund;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BankRefundResult(
        // 원거래 식별자.
        String pgPaymentId,

        // 최초 환불 요청 키.
        String refundRequestId,

        // 최초 요청 금액.
        BigDecimal amount,

        // 금융 확정 결과. SUCCEEDED 또는 WITHDRAWN만 수신 확인으로 인정한다.
        String outcome,

        // 최초 금융 결과를 확정한 시각.
        LocalDateTime completedAt,

        // 원문을 포함하지 않는 최초 요청 검증 해시.
        String requestHash
) {

    // 금액·요청·작업 결과가 원요청과 일치하는지 확인한다.
    public boolean matches(BankRefundCommand request) {

        return request.pgPaymentId().equals(pgPaymentId) && request.refundRequestId().equals(refundRequestId)
                && amount != null && request.amount().compareTo(amount) == 0 && completedAt != null
                && ("SUCCEEDED".equals(outcome) || "WITHDRAWN".equals(outcome))
                && request.fingerprint().equals(requestHash);
    }
}
