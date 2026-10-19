package dev.bum.payment_gateway_service.jpa.refund;

public enum GatewayRefundStatus {
    REQUESTED,
    SUCCEEDED,
    FAILED,
    CLOSED
}
