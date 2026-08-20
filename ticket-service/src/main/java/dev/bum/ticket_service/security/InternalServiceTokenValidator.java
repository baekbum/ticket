package dev.bum.ticket_service.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalServiceTokenValidator {

    private final String serviceToken;

    public InternalServiceTokenValidator(
            @Value("${app.internal.service-token:local-internal-service-token}") String serviceToken
    ) {
        this.serviceToken = serviceToken;
    }

    public void validate(String requestedToken) {
        if (!StringUtils.hasText(requestedToken)
                || !MessageDigest.isEqual(
                serviceToken.getBytes(StandardCharsets.UTF_8),
                requestedToken.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new AccessDeniedException("내부 서비스 인증 토큰이 유효하지 않습니다.");
        }
    }
}
