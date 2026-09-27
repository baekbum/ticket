package dev.bum.payment_gateway_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class PaymentProviderAuthenticationFilter extends OncePerRequestFilter {

    public static final String PROVIDER_TOKEN_HEADER = "X-Payment-Provider-Token";

    private final PaymentProviderTokenValidator tokenValidator;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String providerToken = request.getHeader(PROVIDER_TOKEN_HEADER);
        if (providerToken != null) {
            try {
                tokenValidator.validate(providerToken);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "payment-provider",
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_PAYMENT_PROVIDER"))
                        )
                );
            } catch (RuntimeException exception) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
