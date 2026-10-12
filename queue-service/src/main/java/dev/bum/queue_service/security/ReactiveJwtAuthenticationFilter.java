package dev.bum.queue_service.security;

import dev.bum.common.jwt.JwtTokenProvider;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Slf4j
@RequiredArgsConstructor
public class ReactiveJwtAuthenticationFilter implements WebFilter {
    private final JwtTokenProvider jwtTokenProvider;
    private final ReactiveTokenStateStore tokenStateStore;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Mono.defer(() -> {
            String bearer = exchange.getRequest().getHeaders().getFirst("Authorization");

            if (!StringUtils.hasText(bearer) || !bearer.startsWith("Bearer ")) {
                return chain.filter(exchange);
            }

            return authenticate(bearer.substring(7))
                    .flatMap(result -> {
                        if (result.authentication() == null) {
                            return writeError(exchange, result.status(), result.message());
                        }
                        // 스레드 로컬 대신 Reactor Context를 통해 후속 필터와 컨트롤러에 전달한다.
                        return chain.filter(exchange)
                                .contextWrite(
                                        ReactiveSecurityContextHolder.withAuthentication(result.authentication()));
                    });
        });
    }

    private Mono<AuthResult> authenticate(String token) {
        try {
            jwtTokenProvider.validateToken(token);

            if (!"access".equals(jwtTokenProvider.getTokenType(token))) {
                return Mono.just(rejected(HttpStatus.UNAUTHORIZED, "액세스 토큰이 필요합니다."));
            }

            String userId = jwtTokenProvider.getUserId(token);
            long version = jwtTokenProvider.getTokenVersion(token);
            String role = jwtTokenProvider.getRole(token);
            Authentication authentication = jwtTokenProvider.getAuthentication(token);
            // 인증 저장소의 오류만 503으로 변환한다. 후속 서비스 오류는 여기서 처리하지 않는다.
            return Mono.defer(() -> tokenStateStore.get(userId))
                    .map(state -> state.active() && state.version() == version && state.role().equals(role)
                            ? AuthResult.builder().authentication(authentication).build()
                            : rejected(HttpStatus.UNAUTHORIZED, "인증이 만료되었거나 취소되었습니다. 다시 로그인해주세요."))
                    .defaultIfEmpty(rejected(HttpStatus.UNAUTHORIZED, "인증이 만료되었거나 취소되었습니다. 다시 로그인해주세요."))
                    .doOnError(error -> log.error("인증 상태 Redis 조회 실패. userId={}", userId, error))
                    .onErrorReturn(rejected(HttpStatus.SERVICE_UNAVAILABLE, "인증 상태를 확인할 수 없습니다. 잠시 후 다시 시도해주세요."));
        } catch (ExpiredJwtException error) {
            return Mono.just(rejected(HttpStatus.UNAUTHORIZED, "액세스 토큰이 만료되었습니다. 다시 로그인해주세요."));
        } catch (JwtException | IllegalArgumentException error) {
            return Mono.just(rejected(HttpStatus.FORBIDDEN, "유효하지 않거나 변조된 토큰입니다."));
        }
    }

    private AuthResult rejected(HttpStatus status, String message) {
        return AuthResult.builder()
                .status(status)
                .message(message)
                .build();
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(
                new MediaType("application", "json", StandardCharsets.UTF_8));
        byte[] body = ("{\"status\": " + status.value() + ", \"error\": \"" + message + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    @Builder
    private record AuthResult(Authentication authentication, HttpStatus status, String message) { }
}
