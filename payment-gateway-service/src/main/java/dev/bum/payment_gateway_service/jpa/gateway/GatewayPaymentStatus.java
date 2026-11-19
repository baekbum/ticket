package dev.bum.payment_gateway_service.jpa.gateway;

public enum GatewayPaymentStatus {

    // 가맹점 거래를 등록하고 금융 처리를 기다리는 상태.
    READY,

    // 가상계좌가 발급되어 입금을 기다리는 상태.
    WAITING_DEPOSIT,

    // 금융 처리가 완료된 상태. 가맹점 통보 상태는 별도로 관리한다.
    PAID,

    // 성공한 환불 누적액이 원결제 금액보다 작은 상태.
    PARTIALLY_REFUNDED,

    // 원결제 금액의 환불이 모두 완료된 상태.
    REFUNDED,

    // 결제 취소가 확정된 상태. 실제 자금 반환과는 구분한다.
    CANCELLED,

    // 해당 결제수단의 처리 기한이 만료된 상태.
    EXPIRED,

    // 재사용할 수 없는 업무 실패 상태. 통보 실패에는 사용하지 않는다.
    FAILED
}
