package dev.bum.common.security;

import dev.bum.common.jwt.JwtTokenProvider;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 헤더에서 토큰 추출
        String token = resolveToken(request);

        try {
            // 2. 토큰 유효성 검사
            if (token != null && jwtTokenProvider.validateToken(token)) {
                // 3. 토큰이 유효하면 인증 객체(Authentication)를 시큐리티 컨텍스트에 저장
                Authentication auth = jwtTokenProvider.getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }

            // 정상적인 토큰이거나 토큰이 없는 요청(permitALL 대상 등)은 다음 필터로 통과
            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            log.warn("🚨 [필터 감지] Access Token이 만료되었습니다. -> 401 반환");
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Access Token Expired");

        } catch (JwtException | IllegalArgumentException e) {
            log.warn("🚨 [필터 감지] 유효하지 않거나 변조된 토큰 공격입니다. -> 403 반환: {}", e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "Invalid or Tampered Token");
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * 🌟 클라이언트(프론트엔드)에 JSON 형태로 에러 응답을 직접 내려주는 헬퍼 메서드
     */
    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");

        // 프론트엔드 인터셉터나 개발자 도구에서 깨끗하게 인지할 수 있도록 표준 JSON 포맷으로 작성
        String jsonResponse = String.format("{\"status\": %d, \"error\": \"%s\"}", status, message);
        response.getWriter().write(jsonResponse);
    }
}