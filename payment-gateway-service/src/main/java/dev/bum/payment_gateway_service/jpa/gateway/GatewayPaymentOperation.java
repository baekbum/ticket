package dev.bum.payment_gateway_service.jpa.gateway;

public enum GatewayPaymentOperation {

    // 가맹점 결제번호와 PG 거래를 최초로 연결하는 작업.
    REGISTER,

    // 카드 정보를 검증하고 원승인을 반영하는 작업.
    APPROVE,

    // 입금받을 가상계좌를 발급하는 작업.
    ISSUE_ACCOUNT,

    // 최초 입금 사실을 반영하는 작업.
    DEPOSIT,

    // 거래 전체의 취소를 확정하는 작업.
    CANCEL,

    // 확정적인 거절 결과에 따라 카드 승인을 보상 취소하는 작업.
    CANCEL_APPROVAL,

    // 결제수단의 처리 기한 만료를 확정하는 작업.
    EXPIRE,

    // 개별 요청 식별자로 부분 또는 전체 환불을 반영하는 작업.
    REFUND,

    // 승인 전 허용된 결제수단 전환을 반영하는 작업.
    CHANGE_METHOD,

    // 검증된 기존 금융 거래를 PG 모델로 이전하는 작업.
    MIGRATE
}
