package dev.bum.queue_service.security;

import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.TokenState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReactiveJwtAuthenticationFilterTest {
    private static final String SECRET = "queue-baseline-only-secret-at-least-32-bytes-long";
    private final JwtTokenProvider jwt = new JwtTokenProvider(SECRET, 900000, 1209600000);
    private ReactiveTokenStateStore store;
    private ReactiveJwtAuthenticationFilter filter;
    private AtomicInteger calls;
    private WebFilterChain chain;

    @BeforeEach
    void fixture() {
        store = mock(ReactiveTokenStateStore.class);
        filter = new ReactiveJwtAuthenticationFilter(jwt, store);
        calls = new AtomicInteger();
        chain = exchange -> Mono.fromRunnable(calls::incrementAndGet);
    }

    @Test
    void missing_bearer_delegates_without_reading_authentication_state() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/queue/validate"));
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(calls.get()).isEqualTo(1);
        verifyNoInteractions(store);
    }

    @Test
    void valid_token_reads_state_on_subscription_and_propagates_reactor_context() {
        when(store.get("user")).thenReturn(Mono.delay(Duration.ofMillis(10))
                .thenReturn(new TokenState(1L, true, "ROLE_USER")));
        AtomicReference<Authentication> authentication = new AtomicReference<>();
        var exchange = request(jwt.createToken("user", "ROLE_USER").getAccessToken());
        var operation = filter.filter(exchange, ignored -> ReactiveSecurityContextHolder.getContext()
                .doOnNext(context -> authentication.set(context.getAuthentication())).then());
        verifyNoInteractions(store);
        StepVerifier.create(operation).verifyComplete();
        assertThat(authentication.get().getPrincipal()).isEqualTo("user");
        assertThat(authentication.get().getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
        StepVerifier.create(ReactiveSecurityContextHolder.getContext()).verifyComplete();
    }

    @Test
    void missing_inactive_version_mismatch_and_role_mismatch_states_are_revoked() {
        for (Mono<TokenState> state : java.util.List.of(
                Mono.<TokenState>empty(),
                Mono.just(new TokenState(1L, false, "ROLE_USER")),
                Mono.just(new TokenState(2L, true, "ROLE_USER")),
                Mono.just(new TokenState(1L, true, "ROLE_ADMIN")))) {
            when(store.get("user")).thenReturn(state);
            assertRejected(jwt.createToken("user", "ROLE_USER").getAccessToken(), 401, "인증이 만료되었거나 취소되었습니다. 다시 로그인해주세요.");
        }
    }

    @Test
    void refresh_expired_and_malformed_tokens_preserve_error_contract() {
        assertRejected(jwt.createToken("user", "ROLE_USER").getRefreshToken(), 401, "액세스 토큰이 필요합니다.");
        var expired = new JwtTokenProvider(SECRET, -60000, 1209600000);
        assertRejected(expired.createToken("user", "ROLE_USER").getAccessToken(), 401, "액세스 토큰이 만료되었습니다. 다시 로그인해주세요.");
        assertRejected("malformed", 403, "유효하지 않거나 변조된 토큰입니다.");
        verifyNoInteractions(store);
    }

    @Test
    void authentication_redis_failure_returns_503_without_calling_controller() {
        when(store.get("user")).thenReturn(Mono.error(new DataAccessResourceFailureException("인증 Redis 장애")));
        assertRejected(jwt.createToken("user", "ROLE_USER").getAccessToken(), 503, "인증 상태를 확인할 수 없습니다. 잠시 후 다시 시도해주세요.");
    }

    @Test
    void downstream_service_error_is_not_rewritten_as_authentication_failure() {
        when(store.get("user")).thenReturn(Mono.just(new TokenState(1L, true, "ROLE_USER")));
        var failure = new IllegalArgumentException("서비스 입력 오류");
        var exchange = request(jwt.createToken("user", "ROLE_USER").getAccessToken());
        StepVerifier.create(filter.filter(exchange, ignored -> Mono.error(failure)))
                .expectErrorMatches(error -> error == failure).verify();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    private MockServerWebExchange request(String token) {
        return MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/queue/events/1/enter")
                .header("Authorization", "Bearer " + token));
    }

    private void assertRejected(String token, int status, String message) {
        var exchange = request(token);
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(status);
        assertThat(exchange.getResponse().getHeaders().getContentType().toString())
                .isEqualTo("application/json;charset=UTF-8");
        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .expectNext("{\"status\": " + status + ", \"error\": \"" + message + "\"}").verifyComplete();
        assertThat(calls.get()).isZero();
    }
}
