package dev.bum.payment_gateway_service.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentProviderAuthenticationFilterTest {

    private final PaymentProviderAuthenticationFilter filter = new PaymentProviderAuthenticationFilter(
            new PaymentProviderTokenValidator("test-provider-token")
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticates_valid_payment_provider_token() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/v1/payments/virtual-account/deposit"
        );
        request.addHeader(PaymentProviderAuthenticationFilter.PROVIDER_TOKEN_HEADER, "test-provider-token");

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> { });

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_PAYMENT_PROVIDER");
    }

    @Test
    void rejects_invalid_payment_provider_token() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/v1/payments/virtual-account/deposit"
        );
        request.addHeader(PaymentProviderAuthenticationFilter.PROVIDER_TOKEN_HEADER, "wrong-token");

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> { });

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
