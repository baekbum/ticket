package dev.bum.payment_gateway_service.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class InternalServiceAuthenticationFilterTest {

    private final InternalServiceAuthenticationFilter filter = new InternalServiceAuthenticationFilter(
            new InternalServiceTokenValidator("test-service-token")
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticates_valid_internal_service_token() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payments/virtual-account/issue");
        request.addHeader(InternalServiceAuthenticationFilter.SERVICE_TOKEN_HEADER, "test-service-token");

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> { });

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_INTERNAL_SERVICE");
    }

    @Test
    void rejects_invalid_internal_service_token() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payments/virtual-account/issue");
        request.addHeader(InternalServiceAuthenticationFilter.SERVICE_TOKEN_HEADER, "wrong-token");

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> { });

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
