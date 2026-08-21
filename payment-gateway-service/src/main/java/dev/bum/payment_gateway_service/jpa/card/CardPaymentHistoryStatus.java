package dev.bum.payment_gateway_service.jpa.card;

public enum CardPaymentHistoryStatus {

    // payment-gateway에서 카드 검증과 승인 처리가 완료된 상태.
    APPROVED,

    // payment-gateway에서 카드 정보 검증이나 승인 처리가 실패한 상태.
    APPROVAL_FAILED,

    // ticket-service 결제 완료 반영까지 성공한 상태.
    TICKET_PAYMENT_COMPLETED,

    // 카드 승인은 됐지만 ticket-service 결제 완료 요청이 실패한 상태.
    TICKET_PAYMENT_FAILED,

    // 카드 승인 이후 취소나 보상 처리로 사용 내역을 취소한 상태.
    CANCELLED,

    // ticket-service 결제 완료 이후 전체 금액 환불이 완료된 상태.
    REFUNDED,

    // ticket-service 결제 완료 이후 일부 금액 환불이 완료된 상태.
    PARTIALLY_REFUNDED
}
