package dev.bum.auth_service.service;

import dev.bum.auth_service.exception.RedisException;
import dev.bum.auth_service.jpa.Auth;
import dev.bum.auth_service.jpa.AuthRepository;
import dev.bum.common.error.ErrorCode;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.service.user.user.enums.UserRole;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

/** 실제 Redis에서 실행하며 이번 테스트가 만든 사용자 토큰 키만 삭제한다. */
@EnabledIfEnvironmentVariable(named = "TICKET_REDIS_INTEGRATION", matches = "true")
class RefreshTokenRedisIntegrationTest {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private AuthRepository repository;
    private JwtTokenProvider provider;
    private AuthService service;
    private Auth auth;
    private String user;
    private String key;
    private String oldToken;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                System.getenv().getOrDefault("TICKET_TEST_REDIS_HOST", "127.0.0.1"),
                Integer.parseInt(System.getenv().getOrDefault("TICKET_TEST_REDIS_PORT", "6390")));
        config.setDatabase(Integer.parseInt(System.getenv().getOrDefault("TICKET_TEST_REDIS_DATABASE", "15")));
        factory = new LettuceConnectionFactory(config, LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(3)).shutdownTimeout(Duration.ZERO).build());
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        assertThat(redis.execute((RedisCallback<String>) connection -> connection.ping())).isEqualTo("PONG");
        user = "refresh-integration-" + UUID.randomUUID();
        key = "RT:" + user;
        provider = spy(new JwtTokenProvider("refresh-integration-secret-012345678901234567890123456789", 900000L, 1209600000L));
        redis.opsForValue().set("AUTH:STATE:" + user, "1:1:ROLE_USER");
        oldToken = provider.createToken(user, "ROLE_USER").getRefreshToken();
        redis.opsForValue().set(key, oldToken, Duration.ofMinutes(1));
        repository = mock(AuthRepository.class);
        auth = Auth.builder().id(1L).userId(user).role(UserRole.ROLE_USER).password("encoded").build();
        given(repository.findByUserId(user)).willReturn(auth);
        service = new AuthService(repository, mock(LoginAttemptService.class), provider, redis, new dev.bum.common.security.RedisTokenStateStore(redis));
    }

    @AfterEach
    void cleanUp() {
        try {
            if (redis != null && key != null) redis.delete(key);
            if (redis != null && user != null) redis.delete("AUTH:STATE:" + user);
        } finally {
            if (factory != null) factory.destroy();
        }
    }

    @Test
    @DisplayName("같은 토큰으로 16개 재발급 요청이 사전 검증을 통과해도 하나만 성공한다")
    void concurrent_rotation_has_one_winner() throws Exception {
        CyclicBarrier readyToRotate = new CyclicBarrier(16);
        doAnswer(invocation -> {
            readyToRotate.await(10, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(provider).createToken(user, "ROLE_USER", 1L);
        List<Boolean> results = concurrent(16, () -> attempt(() -> service.reissueToken(oldToken)));
        assertThat(results).filteredOn(Boolean::booleanValue).hasSize(1);
        String saved = redis.opsForValue().get(key);
        assertThat(saved).isNotEqualTo(oldToken);
        assertThat(provider.validateToken(saved)).isTrue();
        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(1209590L, 1209600L);
    }

    @Test
    @DisplayName("같은 토큰의 동시 로그아웃은 하나만 삭제에 성공한다")
    void concurrent_logout_has_one_winner() throws Exception {
        CyclicBarrier readyToDelete = new CyclicBarrier(16);
        given(repository.findByUserId(user)).willAnswer(invocation -> {
            readyToDelete.await(10, TimeUnit.SECONDS);
            return auth;
        });
        List<Boolean> results = concurrent(16, () -> attempt(() -> service.logout(oldToken)));
        assertThat(results).filteredOn(Boolean::booleanValue).hasSize(1);
        assertThat(redis.hasKey(key)).isFalse();
    }

    @Test
    @DisplayName("같은 토큰의 재발급과 로그아웃이 경합하면 하나만 성공한다")
    void rotation_and_logout_do_not_both_succeed() throws Exception {
        CyclicBarrier readyToMutate = new CyclicBarrier(2);
        given(repository.findByUserId(user)).willAnswer(invocation -> {
            readyToMutate.await(10, TimeUnit.SECONDS);
            return auth;
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> rotation = executor.submit(() -> attempt(() -> service.reissueToken(oldToken)));
            Future<Boolean> logout = executor.submit(() -> attempt(() -> service.logout(oldToken)));
            boolean rotated = rotation.get(15, TimeUnit.SECONDS);
            boolean deleted = logout.get(15, TimeUnit.SECONDS);
            assertThat(rotated ^ deleted).isTrue();
            if (rotated) assertThat(redis.opsForValue().get(key)).isNotNull().isNotEqualTo(oldToken);
            else assertThat(redis.hasKey(key)).isFalse();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("이전 토큰의 로그아웃은 사전 검증 뒤 생성된 새 세션을 삭제하지 않는다")
    void logout_preserves_new_login_session() {
        String newLogin = replaceSessionDuringUserLookup();
        assertThat(attempt(() -> service.logout(oldToken))).isFalse();
        assertNewSessionPreserved(newLogin);
    }

    @Test
    @DisplayName("이전 토큰의 재발급은 사전 검증 뒤 생성된 새 세션과 TTL을 덮어쓰지 않는다")
    void rotation_preserves_new_login_session() {
        String newLogin = replaceSessionDuringUserLookup();
        assertThat(attempt(() -> service.reissueToken(oldToken))).isFalse();
        assertNewSessionPreserved(newLogin);
    }

    @Test
    @DisplayName("사전 검증 뒤 만료되거나 삭제된 Redis 토큰 키를 재발급으로 다시 생성하지 않는다")
    void missing_key_is_not_recreated() {
        given(repository.findByUserId(user)).willAnswer(invocation -> {
            redis.delete(key);
            return auth;
        });
        assertThat(attempt(() -> service.reissueToken(oldToken))).isFalse();
        assertThat(redis.hasKey(key)).isFalse();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"password", "reset", "role", "withdrawal", "blacklist"})
    void security_events_revoke_both_tokens(String change) throws Exception {
        String access = provider.createToken(user, "ROLE_USER", 1L).getAccessToken();
        var event = dev.bum.common.kafka.user.UserDtoForEvent.builder().userId(user).id(1L).build();
        switch (change) {
            case "password" -> event.setPassword("new-hash");
            case "reset" -> { event.setPassword("new-hash"); event.setPasswordReset(true); }
            case "role" -> event.setRole("ROLE_ADMIN");
            case "withdrawal" -> event.setStatus("WITHDRAWN");
            case "blacklist" -> event.setIsBlacklisted(true);
        }
        given(repository.update(event)).willAnswer(invocation -> { auth.updateInfo(event); return auth; });
        service.updateUserTopic(event);
        assertThat(auth.getTokenVersion()).isEqualTo(2L);
        assertThat(redis.hasKey(key)).isFalse();
        assertThat(attempt(() -> service.reissueToken(oldToken))).isFalse();
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + access);
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var chain = mock(jakarta.servlet.FilterChain.class);
        new dev.bum.common.security.JwtAuthenticationFilter(provider,
                new dev.bum.common.security.RedisTokenStateStore(redis)).doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void duplicate_event_preserves_new_login_and_reissue_keeps_version() {
        var event = dev.bum.common.kafka.user.UserDtoForEvent.builder().userId(user).password("new-hash").build();
        given(repository.update(event)).willAnswer(invocation -> { auth.updateInfo(event); return auth; });
        service.updateUserTopic(event);
        var tokens = service.LoginAndCreateToken(new dev.bum.common.service.auth.dto.LoginRequest(user, "password"));
        assertThat(provider.getTokenVersion(tokens.getAccessToken())).isEqualTo(2L);
        assertThat(provider.getTokenVersion(tokens.getRefreshToken())).isEqualTo(2L);
        service.updateUserTopic(event);
        assertThat(redis.opsForValue().get(key)).isEqualTo(tokens.getRefreshToken());
        var rotated = service.reissueToken(tokens.getRefreshToken());
        assertThat(provider.getTokenVersion(rotated.getAccessToken())).isEqualTo(2L);
        assertThat(provider.getTokenVersion(rotated.getRefreshToken())).isEqualTo(2L);
    }

    @Test
    void revocation_during_rotation_cannot_restore_old_session() throws Exception {
        CountDownLatch created = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        doAnswer(invocation -> {
            var tokens = invocation.callRealMethod();
            created.countDown();
            assertThat(resume.await(10, TimeUnit.SECONDS)).isTrue();
            return tokens;
        }).when(provider).createToken(user, "ROLE_USER", 1L);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> rotated = executor.submit(() -> attempt(() -> service.reissueToken(oldToken)));
            assertThat(created.await(10, TimeUnit.SECONDS)).isTrue();
            new dev.bum.common.security.RedisTokenStateStore(redis)
                    .publish(user, dev.bum.common.security.TokenState.builder().version(2L).active(true).role("ROLE_USER").build());
            resume.countDown();
            assertThat(rotated.get(15, TimeUnit.SECONDS)).isFalse();
            assertThat(redis.hasKey(key)).isFalse();
        } finally {
            resume.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void stale_state_cannot_overwrite_new_version() {
        var states = new dev.bum.common.security.RedisTokenStateStore(redis);
        states.publish(user, dev.bum.common.security.TokenState.builder().version(2L).active(true).role("ROLE_USER").build());
        String newToken = provider.createToken(user, "ROLE_USER", 2L).getRefreshToken();
        redis.opsForValue().set(key, newToken);
        assertThatThrownBy(() -> states.publish(user, dev.bum.common.security.TokenState.builder().version(1L).active(true).role("ROLE_USER").build()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(states.get(user).version()).isEqualTo(2L);
        assertThat(redis.opsForValue().get(key)).isEqualTo(newToken);
    }

    private String replaceSessionDuringUserLookup() {
        String newLogin = provider.createToken(user, "ROLE_USER").getRefreshToken();
        given(repository.findByUserId(user)).willAnswer(invocation -> {
            redis.opsForValue().set(key, newLogin, Duration.ofMinutes(10));
            return auth;
        });
        return newLogin;
    }

    private void assertNewSessionPreserved(String token) {
        assertThat(redis.opsForValue().get(key)).isEqualTo(token);
        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(590L, 600L);
    }

    private boolean attempt(Runnable action) {
        try {
            action.run();
            return true;
        } catch (RedisException e) {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_MISMATCH);
            return false;
        }
    }

    private List<Boolean> concurrent(int count, Callable<Boolean> request) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) futures.add(executor.submit(request));
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) results.add(future.get(15, TimeUnit.SECONDS));
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
