package dev.bum.common.service.ticket.payment.dto;

import lombok.Builder;

@Builder
public record GatewayRefundResolutionResponse(
        // 결제 환불 처리 식별자.
        Long paymentRefundProcessId,

        // 결제 번호.
        String paymentNo,

        // 작업 수행 결과.
        Outcome outcome
) {

    public enum Outcome {
        // 처리 성공 결과.
        SUCCEEDED, // 대상 없음 결과.

        NOT_FOUND, // 처리 요청 상태.

        REQUESTED, // 처리 실패 상태.

        FAILED, // 처리 종료 상태.

        CLOSED
    }
}
