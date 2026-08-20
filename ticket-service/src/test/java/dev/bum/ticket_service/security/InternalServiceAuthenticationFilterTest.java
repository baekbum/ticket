package dev.bum.ticket_service.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class InternalServiceAuthenticationFilterTest {

    private final InternalServiceTokenValidator tokenValidator =
            new InternalServiceTokenValidator("local-internal-service-token");
    private final InternalServiceAuthenticationFilter filter =
            new InternalServiceAuthenticationFilter(tokenValidator);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("내부 결제 API에 유효한 서비스 토큰이 있으면 INTERNAL_SERVICE 권한을 부여한다")
    void authenticate_internal_payment_request() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payments/internal/card/complete");
        request.setServletPath("/api/v1/payments/internal/card/complete");
        request.addHeader("X-Service-Token", "local-internal-service-token");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .contains("ROLE_INTERNAL_SERVICE");
    }

    @Test
    @DisplayName("내부 결제 API에 잘못된 서비스 토큰이 있으면 인증 정보를 만들지 않는다")
    void reject_invalid_internal_payment_token() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payments/internal/card/complete");
        request.setServletPath("/api/v1/payments/internal/card/complete");
        request.addHeader("X-Service-Token", "invalid-token");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("내부 결제 API가 아닌 요청은 서비스 토큰 검증을 수행하지 않는다")
    void skip_non_internal_payment_request() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payments/card/complete");
        request.setServletPath("/api/v1/payments/card/complete");
        request.addHeader("X-Service-Token", "local-internal-service-token");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
