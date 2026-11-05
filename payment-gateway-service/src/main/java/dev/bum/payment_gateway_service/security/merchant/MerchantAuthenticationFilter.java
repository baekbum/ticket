package dev.bum.payment_gateway_service.security.merchant;

import dev.bum.payment_gateway_service.security.InternalServiceAuthenticationFilter;
import dev.bum.payment_gateway_service.service.merchant.MerchantAuthenticationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class MerchantAuthenticationFilter extends OncePerRequestFilter {

    // 서비스 토큰을 DB에 등록된 가맹점과 연결하는 인증 서비스.
    private final MerchantAuthenticationService authenticationService;

    // 가맹점 전용 API의 토큰을 검증하고 인증된 숫자 가맹점 코드를 요청에 연결한다.
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        AuthenticatedMerchant merchant;

        try {
            merchant = authenticationService.authenticate(
                    request.getHeader(InternalServiceAuthenticationFilter.SERVICE_TOKEN_HEADER));
        } catch (AuthenticationException exception) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "MERCHANT_AUTHENTICATION_REQUIRED");
            return;
        } catch (DataAccessException exception) {
            reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "MERCHANT_AUTHENTICATION_UNAVAILABLE");
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                merchant, null, List.of(new SimpleGrantedAuthority("ROLE_MERCHANT"))));

        chain.doFilter(request, response);
    }

    // 실패한 인증 상태를 제거하고 토큰이나 DB 오류 상세가 없는 응답을 반환한다.
    private void reject(HttpServletResponse response, int status, String code) throws IOException {

        SecurityContextHolder.clearContext();

        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"가맹점 인증을 확인할 수 없습니다.\"}");
    }
}
