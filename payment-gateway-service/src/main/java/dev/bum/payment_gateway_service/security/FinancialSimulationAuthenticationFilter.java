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
public class FinancialSimulationAuthenticationFilter extends OncePerRequestFilter {

    public static final String TOKEN_HEADER = "X-PG-Simulation-Token";
    public static final String USER_HEADER = "X-PG-User-Id";
    private final FinancialSimulationToken token;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        if (!token.matches(request.getHeader(TOKEN_HEADER))) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"FINANCIAL_SIMULATION_AUTHENTICATION_REQUIRED\"}");
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "pg-financial-client", null, List.of(new SimpleGrantedAuthority("ROLE_FINANCIAL_SIMULATOR"))));

        chain.doFilter(request, response);
    }
}
