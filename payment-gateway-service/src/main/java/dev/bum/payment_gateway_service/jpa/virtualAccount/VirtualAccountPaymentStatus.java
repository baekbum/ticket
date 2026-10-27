package dev.bum.payment_gateway_service.jpa.virtualAccount;

public enum VirtualAccountPaymentStatus {

    // 가상계좌가 발급되어 입금을 기다리는 상태.
    WAITING_DEPOSIT,

    // 입금이 완료된 상태. 티켓 반영 여부는 입금 Outbox에서 관리한다.
    DEPOSITED,

    // 입금 기한이 지나 더 이상 입금할 수 없는 상태.
    EXPIRED,

    // 계좌 발급 이후 취소된 상태.
    CANCELLED
}
