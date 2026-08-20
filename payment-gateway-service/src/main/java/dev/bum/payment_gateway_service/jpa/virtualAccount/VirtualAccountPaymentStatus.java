package dev.bum.payment_gateway_service.jpa.virtualAccount;

public enum VirtualAccountPaymentStatus {

    // 가상계좌가 발급되어 입금을 기다리는 상태.
    WAITING_DEPOSIT,

    // 사용자가 입금했고 ticket-service 결제 완료 반영을 기다리는 상태.
    DEPOSITED,

    // ticket-service 결제 완료 반영까지 성공한 상태.
    TICKET_PAYMENT_COMPLETED,

    // ticket-service 결제 완료 반영에 실패한 상태.
    TICKET_PAYMENT_FAILED,

    // 입금 기한이 지나 더 이상 입금할 수 없는 상태.
    EXPIRED,

    // 계좌 발급 이후 취소된 상태.
    CANCELLED
}
