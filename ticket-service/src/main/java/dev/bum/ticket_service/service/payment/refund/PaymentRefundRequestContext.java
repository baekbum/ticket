package dev.bum.ticket_service.service.payment.refund;

import lombok.Builder;

/** 예매 잠금 아래 확인한 기존 환불 요청과 이번 요청의 비교 결과. */
@Builder
public record PaymentRefundRequestContext(
        // 이전 환불 처리 식별자.
        Long previousProcessId,

        // 기존 요청과 동일한 요청인지 여부.
        boolean sameRequest
) {

    // 이전에 생성된 환불 요청이 있는지 확인한다.
    public boolean hasPreviousRequest() {

        return previousProcessId != null;
    }
}
