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
public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {

    public static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final InternalServiceTokenValidator tokenValidator;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String serviceToken = request.getHeader(SERVICE_TOKEN_HEADER);
        if (serviceToken != null) {
            try {
                tokenValidator.validate(serviceToken);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "ticket-service",
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE"))
                        )
                );
            } catch (RuntimeException exception) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
