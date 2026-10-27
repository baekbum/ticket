package dev.bum.ticket_service.jpa.payment.payment;

public enum PaymentHistoryType {
    MIGRATED,
    CREATED,
    APPROVAL_REQUESTED,
    METHOD_CHANGED,
    ACCOUNT_ISSUED,
    GATEWAY_APPROVED,
    COMPLETED,
    FAILED,
    EXPIRED,
    CANCELLED,
    REFUNDED,
    CANCELLATION_FEE_APPLIED,
    RETRY_REQUESTED
}
