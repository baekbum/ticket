package dev.bum.payment_gateway_service.security.merchant;

public record AuthenticatedMerchant(
        // DB 해시 검증을 통과한 숫자 가맹점 코드. 원문 키와 해시는 인증 주체에 보관하지 않는다.
        Long merchantId
) { }
