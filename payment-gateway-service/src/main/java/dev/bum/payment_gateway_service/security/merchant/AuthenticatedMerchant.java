package dev.bum.payment_gateway_service.security.merchant;

public record AuthenticatedMerchant(
        // DB에서 자격증명에 연결된 숫자 가맹점 코드.
        Long merchantId,

        // 인증에 사용한 설정 그룹. 토큰 원문은 보관하지 않는다.
        String credentialKey
) { }
