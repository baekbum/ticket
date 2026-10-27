package dev.bum.payment_gateway_service.jpa.virtualAccount;

public enum VirtualAccountPaymentHistoryType {

    // 가상계좌가 최초 발급된 이력.
    ISSUED,

    // 입금이 확인된 이력.
    DEPOSITED,

    // 무통장 결제의 전액 또는 부분 환불 완료 이력.
    REFUNDED,

    // 입금 기한 만료 이력.
    EXPIRED,

    // 계좌 발급 취소 이력.
    CANCELLED
}
