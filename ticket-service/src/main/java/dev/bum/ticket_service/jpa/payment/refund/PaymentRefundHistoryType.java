package dev.bum.ticket_service.jpa.payment.refund;

public enum PaymentRefundHistoryType {
    MIGRATED,
    REQUESTED,
    RETRY_REQUESTED,
    GATEWAY_SUCCEEDED,
    GATEWAY_FAILED,
    LOCAL_APPLIED,
    LOCAL_SUCCEEDED,
    LOCAL_FAILED
}
