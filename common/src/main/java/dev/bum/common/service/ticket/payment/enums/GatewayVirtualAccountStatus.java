package dev.bum.common.service.ticket.payment.enums;

/**
 * PG 가상계좌의 현재 처리 상태입니다.
 */
public enum GatewayVirtualAccountStatus {
    WAITING_DEPOSIT,
    DEPOSITED,
    TICKET_PAYMENT_COMPLETED,
    TICKET_PAYMENT_FAILED,
    EXPIRED,
    CANCELLED
}
