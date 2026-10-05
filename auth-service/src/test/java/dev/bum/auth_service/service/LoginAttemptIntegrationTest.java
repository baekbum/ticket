package dev.bum.auth_service.service;

import dev.bum.auth_service.exception.PasswordIncorrectException;
import dev.bum.auth_service.exception.UserLoginLockedException;
import dev.bum.auth_service.exception.UserNotExistException;
import dev.bum.auth_service.jpa.*;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.kafka.user.UserDtoForEvent;
import dev.bum.common.service.auth.dto.LoginRequest;
import dev.bum.common.service.user.user.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

@DataJpaTest
@ActiveProfiles("test")
@Import({LoginAttemptService.class, AuthService.class, AuthRepositoryImpl.class,
        LoginAttemptIntegrationTest.PasswordConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LoginAttemptIntegrationTest {
    @Autowired private LoginAttemptService attempts;
    @Autowired private AuthService authService;
    @Autowired private AuthRepository repository;
    @Autowired private AuthJpaRepository auths;
    @Autowired private UserLoginLockJpaRepository locks;
    @Autowired private PasswordEncoder encoder;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private JwtTokenProvider tokenProvider;
    @MockitoBean private StringRedisTemplate redis;
    @MockitoBean private dev.bum.common.security.TokenStateStore tokenStateStore;

    @TestConfiguration
    static class PasswordConfiguration {
        @Bean PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }
    }

    @BeforeEach
    void setUp() {
        locks.deleteAll();
        auths.deleteAll();
        auths.saveAndFlush(Auth.builder().id(1L).userId("user01")
                .password(encoder.encode("correct-password")).role(UserRole.ROLE_USER).build());
    }

    @Test
    @DisplayName("로그인 예외로 외부 트랜잭션이 롤백되어도 실패 횟수는 저장된다")
    void failure_survives_outer_rollback() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                authService.LoginAndCreateToken(new LoginRequest("USER01", "wrong"))))
                .isInstanceOf(PasswordIncorrectException.class);
        assertThat(lock().getFailedAttempts()).isEqualTo(1);
        assertThat(lock().getLastFailedAt()).isNotNull();
        verifyNoInteractions(tokenProvider, redis);
    }

    @Test
    @DisplayName("다섯 번째 실패부터 잠기고 올바른 비밀번호로도 로그인할 수 없다")
    void fifth_failure_locks_account() {
        failFourTimes();
        assertThatThrownBy(() -> attempts.validatePassword(1L, "wrong"))
                .isInstanceOf(UserLoginLockedException.class);
        assertThatThrownBy(() -> authService.LoginAndCreateToken(new LoginRequest("user01", "correct-password")))
                .isInstanceOf(UserLoginLockedException.class);
        assertThat(lock().getFailedAttempts()).isEqualTo(5);
        assertThat(lock().getLockedAt()).isNotNull();
        verifyNoInteractions(tokenProvider, redis);
    }

    @Test
    @DisplayName("잠금 전 정상 인증은 연속 실패 횟수를 초기화한다")
    void successful_authentication_resets_failures() {
        failFourTimes();
        attempts.validatePassword(1L, "correct-password");
        assertThat(lock().getFailedAttempts()).isZero();
        assertThat(lock().isLocked()).isFalse();
        assertThatThrownBy(() -> attempts.validatePassword(1L, "wrong"))
                .isInstanceOf(PasswordIncorrectException.class);
        assertThat(lock().getFailedAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("최초 잠금 행이 없는 계정에 동시 실패가 들어와도 한 행에 다섯 번까지만 기록한다")
    void simultaneous_failures_are_serialized() throws Exception {
        int requestCount = 12;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CyclicBarrier start = new CyclicBarrier(requestCount);
        try {
            List<Future<Class<?>>> responses = new ArrayList<>();
            for (int i = 0; i < requestCount; i++) {
                responses.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        attempts.validatePassword(1L, "wrong");
                        throw new AssertionError("잘못된 비밀번호로 인증에 성공했습니다.");
                    } catch (PasswordIncorrectException | UserLoginLockedException e) {
                        return e.getClass();
                    }
                }));
            }
            List<Class<?>> results = new ArrayList<>();
            for (Future<Class<?>> response : responses) {
                results.add(response.get(20, TimeUnit.SECONDS));
            }
            assertThat(results.stream().filter(PasswordIncorrectException.class::equals).count()).isEqualTo(4);
            assertThat(results.stream().filter(UserLoginLockedException.class::equals).count()).isEqualTo(8);
            assertThat(locks.count()).isEqualTo(1);
            assertThat(lock().getFailedAttempts()).isEqualTo(5);
            assertThat(lock().isLocked()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void recreated_account_uses_higher_version_and_ignores_old_delete_event() {
        var deleted = UserDtoForEvent.builder().id(1L).userId("user01").build();
        authService.deleteUserTopic(deleted);
        org.mockito.Mockito.when(tokenStateStore.get("user01"))
                .thenReturn(dev.bum.common.security.TokenState.builder().version(2L).active(false).role("ROLE_USER").build());
        authService.insertUserTopic(UserDtoForEvent.builder().id(2L).userId("user01")
                .password(encoder.encode("new-password")).role("ROLE_USER").build());
        assertThat(auths.findById(2L).orElseThrow().getTokenVersion()).isEqualTo(3L);
        org.mockito.Mockito.clearInvocations(tokenStateStore);
        authService.deleteUserTopic(deleted);
        assertThat(auths.existsById(2L)).isTrue();
        org.mockito.Mockito.verifyNoInteractions(tokenStateStore);
    }

    @Test
    void deleted_account_revocation_is_retried_after_redis_failure() {
        var event = UserDtoForEvent.builder().id(1L).userId("user01").build();
        org.mockito.Mockito.doThrow(new IllegalStateException("Redis unavailable")).doNothing()
                .when(tokenStateStore).publish(org.mockito.ArgumentMatchers.eq("user01"), org.mockito.ArgumentMatchers.any());
        assertThatThrownBy(() -> authService.deleteUserTopic(event))
                .isInstanceOf(dev.bum.auth_service.exception.RedisException.class);
        assertThat(auths.existsById(1L)).isFalse();
        org.mockito.Mockito.when(tokenStateStore.get("user01"))
                .thenReturn(dev.bum.common.security.TokenState.builder().version(1L).active(true).role("ROLE_USER").build());
        authService.deleteUserTopic(event);
        org.mockito.Mockito.verify(tokenStateStore, org.mockito.Mockito.times(2))
                .publish("user01", dev.bum.common.security.TokenState.builder().version(2L).active(false).role("ROLE_USER").build());
    }

    @Test
    void redis_failure_after_commit_can_be_retried_without_incrementing_version_again() {
        var event = resetEvent();
        org.mockito.Mockito.doThrow(new IllegalStateException("Redis unavailable")).doNothing()
                .when(tokenStateStore).publish(org.mockito.ArgumentMatchers.eq("user01"), org.mockito.ArgumentMatchers.any());
        assertThatThrownBy(() -> authService.updateUserTopic(event))
                .isInstanceOf(dev.bum.auth_service.exception.RedisException.class);
        assertThat(auths.findById(1L).orElseThrow().getTokenVersion()).isEqualTo(2L);
        authService.updateUserTopic(event);
        assertThat(auths.findById(1L).orElseThrow().getTokenVersion()).isEqualTo(2L);
        org.mockito.Mockito.verify(tokenStateStore, org.mockito.Mockito.times(2))
                .publish("user01", dev.bum.common.security.TokenState.builder().version(2L).active(true).role("ROLE_USER").build());
    }

    @Test
    void rollback_does_not_publish_token_revocation() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            authService.updateUserTopic(resetEvent());
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(auths.findById(1L).orElseThrow().getTokenVersion()).isEqualTo(1L);
        org.mockito.Mockito.verifyNoInteractions(tokenStateStore);
    }

    @Test
    @DisplayName("비밀번호 재설정 이벤트는 비밀번호 갱신과 잠금 해제를 함께 처리한다")
    void password_reset_unlocks_account() {
        lockAccount();
        update(resetEvent());
        assertThat(lock().getFailedAttempts()).isZero();
        assertThat(lock().getLockedAt()).isNull();
        attempts.validatePassword(1L, "new-password");
        assertThatThrownBy(() -> attempts.validatePassword(1L, "correct-password"))
                .isInstanceOf(PasswordIncorrectException.class);
    }

    @Test
    @DisplayName("일반 비밀번호 변경 이벤트로는 잠금을 해제하지 않는다")
    void ordinary_update_does_not_unlock() {
        lockAccount();
        UserDtoForEvent event = resetEvent();
        event.setPasswordReset(null);
        update(event);
        assertThat(lock().isLocked()).isTrue();
        assertThatThrownBy(() -> attempts.validatePassword(1L, "new-password"))
                .isInstanceOf(UserLoginLockedException.class);
    }

    @Test
    @DisplayName("재설정 이벤트가 중복 전달되어도 재설정 이후 발생한 잠금을 해제하지 않는다")
    void repeated_reset_event_preserves_new_lock() {
        UserDtoForEvent event = resetEvent();
        update(event);
        lockAccount();
        update(event);
        assertThat(lock().isLocked()).isTrue();
        assertThat(lock().getFailedAttempts()).isEqualTo(5);
    }

    @Test
    @DisplayName("재설정 처리 트랜잭션이 실패하면 비밀번호 변경과 잠금 해제가 함께 롤백된다")
    void reset_rollback_preserves_password_and_lock() {
        lockAccount();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            repository.update(resetEvent());
            throw new IllegalStateException("재설정 처리 실패");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(lock().isLocked()).isTrue();
        assertThat(encoder.matches("correct-password", auths.findById(1L).orElseThrow().getPassword())).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 계정에는 잠금 행을 생성하지 않는다")
    void nonexistent_account_has_no_lock() {
        assertThatThrownBy(() -> attempts.validatePassword(99L, "wrong"))
                .isInstanceOf(UserNotExistException.class);
        assertThat(locks.count()).isZero();
    }

    private UserLoginLock lock() {
        return new TransactionTemplate(transactionManager).execute(tx -> locks.findByAuthId(1L).orElseThrow());
    }

    private void failFourTimes() {
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> attempts.validatePassword(1L, "wrong"))
                    .isInstanceOf(PasswordIncorrectException.class);
        }
    }

    private void lockAccount() {
        failFourTimes();
        assertThatThrownBy(() -> attempts.validatePassword(1L, "wrong"))
                .isInstanceOf(UserLoginLockedException.class);
    }

    private UserDtoForEvent resetEvent() {
        return UserDtoForEvent.builder().userId("user01").password(encoder.encode("new-password"))
                .passwordReset(true).build();
    }

    private void update(UserDtoForEvent event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> repository.update(event));
    }
}
