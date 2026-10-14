package dev.bum.ticket_service.jpa.payment.payment;

public enum PaymentProcessStage {
    READY_FOR_APPROVAL,
    APPROVAL_REQUESTED,
    WAITING_DEPOSIT,
    GATEWAY_APPROVED,
    LOCAL_COMPLETED,
    FAILED,
    EXPIRED,
    CANCELLED
}
