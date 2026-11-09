package dev.bum.payment_gateway_service.dto.gateway;

import java.net.URI;

public record MerchantNotificationTarget(
        // 등록 설정에서 확인한 전체 수신 API URL.
        URI url,

        // DB 암호문을 복호화한 발신 인증값. 통보 payload나 로그에 포함하지 않는다.
        String serviceToken
) {

    // 기본 record 문자열 표현으로 인증값이 출력되지 않게 한다.
    @Override
    public String toString() {

        return "MerchantNotificationTarget[REDACTED]";
    }
}
