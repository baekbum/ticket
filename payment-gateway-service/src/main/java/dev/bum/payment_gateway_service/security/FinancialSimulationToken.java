package dev.bum.payment_gateway_service.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class FinancialSimulationToken {

    // PG와 모의 금융 컨트롤러만 공유한다. 환경 설정이 없으면 프로세스마다 새 비밀값을 생성한다.
    private final String value;

    public FinancialSimulationToken(String configuredToken) {

        if (configuredToken == null || configuredToken.isBlank()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            value = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        } else {
            value = configuredToken;
        }
    }

    // HTTP 클라이언트의 내부 인증 헤더에만 사용한다. 로그나 응답에 노출하지 않는다.
    public String value() {

        return value;
    }

    // 일반 사용자 JWT나 Ticksy 서비스 토큰과 독립된 모의 금융 인증값을 비교한다.
    public boolean matches(String requestedToken) {

        return requestedToken != null && requestedToken.length() <= 4096
                && MessageDigest.isEqual(value.getBytes(StandardCharsets.UTF_8),
                requestedToken.getBytes(StandardCharsets.UTF_8));
    }
}
