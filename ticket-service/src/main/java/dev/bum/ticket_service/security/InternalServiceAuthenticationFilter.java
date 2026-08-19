package dev.bum.ticket_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {

    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    private static final RequestMatcher INTERNAL_PAYMENT_REQUEST_MATCHER =
            new AntPathRequestMatcher("/api/*/payments/internal/**");

    private final InternalServiceTokenValidator internalServiceTokenValidator;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (isInternalPaymentRequest(request)) {
            authenticateInternalService(request);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateInternalService(HttpServletRequest request) {
        try {
            internalServiceTokenValidator.validate(request.getHeader(SERVICE_TOKEN_HEADER));
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    "internal-service",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE"))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RuntimeException e) {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isInternalPaymentRequest(HttpServletRequest request) {
        return INTERNAL_PAYMENT_REQUEST_MATCHER.matches(request);
    }
}
