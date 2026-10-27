package dev.bum.ticket_service.service.payment.refund;

import lombok.Builder;

/** 예매 잠금 아래 확인한 기존 환불 요청과 이번 요청의 비교 결과. */
@Builder
public record PaymentRefundRequestContext(Long previousProcessId, boolean sameRequest) {
    public boolean hasPreviousRequest() {
        return previousProcessId != null;
    }
}
