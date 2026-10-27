package dev.bum.ticket_service.service.payment.refund;

import dev.bum.ticket_service.jpa.payment.refund.PaymentRefundProcessStatus;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PaymentRefundRollbackRecord(
        Long processId,
        Long reservationId,
        LocalDateTime expectedUpdatedAt,
        PaymentRefundProcessStatus observedStatus,
        String failureReason
) {
}
