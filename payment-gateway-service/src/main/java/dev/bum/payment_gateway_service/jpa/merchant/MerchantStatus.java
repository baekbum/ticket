package dev.bum.payment_gateway_service.jpa.merchant;

public enum MerchantStatus {

    // 신규 결제를 준비하고 처리할 수 있는 가맹점.
    ACTIVE,

    // 신규 처리는 중단하고 기존 거래의 조회·정리는 허용하는 가맹점.
    SUSPENDED
}
