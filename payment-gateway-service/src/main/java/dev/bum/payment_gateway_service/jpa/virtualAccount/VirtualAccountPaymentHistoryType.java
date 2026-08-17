package dev.bum.payment_gateway_service.jpa.virtualAccount;

public enum VirtualAccountPaymentHistoryType {

    // 가상계좌가 최초 발급된 이력.
    ISSUED,

    // 입금이 확인된 이력.
    DEPOSITED,

    // 입금 완료 Kafka 이벤트 발행 이력.
    DEPOSIT_EVENT_PUBLISHED,

    // ticket-service 결제 완료 반영에 성공한 이력.
    TICKET_PAYMENT_COMPLETED,

    // ticket-service 결제 완료 반영에 실패한 이력.
    TICKET_PAYMENT_FAILED,

    // 입금 기한 만료 이력.
    EXPIRED,

    // 계좌 발급 취소 이력.
    CANCELLED
}
