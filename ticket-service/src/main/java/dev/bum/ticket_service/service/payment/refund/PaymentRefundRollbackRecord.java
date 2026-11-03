package dev.bum.ticket_service.service.payment.refund;

import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcessStatus;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PaymentRefundRollbackRecord(
        // 환불 처리 식별자.
        Long processId,

        // 예매 식별자.
        Long reservationId,

        // 동시성 검증에 사용할 예상 수정 시각.
        LocalDateTime expectedUpdatedAt,

        // 롤백 감시 시점의 처리 상태.
        PaymentRefundProcessStatus observedStatus,

        // 실패 사유.
        String failureReason
) {
}
