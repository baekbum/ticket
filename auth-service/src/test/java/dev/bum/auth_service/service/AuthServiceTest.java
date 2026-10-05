package dev.bum.auth_service.service;

import dev.bum.auth_service.exception.PasswordIncorrectException;
import dev.bum.auth_service.exception.RedisException;
import dev.bum.auth_service.exception.UserNotExistException;
import dev.bum.auth_service.exception.WithdrawnUserException;
import dev.bum.auth_service.jpa.Auth;
import dev.bum.auth_service.jpa.AuthRepository;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.jwt.dto.TokenResponse;
import dev.bum.common.kafka.user.UserDtoForEvent;
import dev.bum.common.service.auth.dto.LoginRequest;
import dev.bum.common.service.user.user.enums.UserRole;
import dev.bum.common.service.user.user.enums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import dev.bum.common.error.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock private dev.bum.common.security.TokenStateStore tokenStateStore;

    @org.junit.jupiter.api.BeforeEach
    void tokenStateDefaults() {
        org.mockito.Mockito.lenient().when(tokenProvider.getTokenType(anyString())).thenReturn("refresh");
        org.mockito.Mockito.lenient().when(tokenProvider.getTokenVersion(anyString())).thenReturn(1L);
        org.mockito.Mockito.lenient().when(authRepository.update(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    UserDtoForEvent event = invocation.getArgument(0);
                    Auth auth = auth(event.getUserId());
                    auth.updateInfo(event);
                    return auth;
                });
        org.mockito.Mockito.lenient().when(authRepository.findByUserId("user01")).thenReturn(auth("user01"));
        org.mockito.Mockito.lenient().when(redisTemplate.execute(eq(RefreshTokenScripts.SAVE), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(1L);
    }


    @Test
    @DisplayName("관리자는 사용자 로그인으로 토큰을 발급받거나 로그인 실패 횟수를 변경할 수 없다")
    void user_login_rejects_admin() {
        Auth admin = Auth.builder().id(1L).userId("admin").role(UserRole.ROLE_ADMIN).build();
        given(authRepository.findByUserId("admin")).willReturn(admin);

        assertThatThrownBy(() -> authService.LoginAndCreateToken(new LoginRequest(" ADMIN ", "password")))
                .isInstanceOf(PasswordIncorrectException.class)
                .hasMessage(ErrorCode.LOGIN_FAILED.getMessage());

        verifyNoInteractions(loginAttemptService, tokenProvider, redisTemplate);
    }

    @Test
    @DisplayName("일반 회원은 관리자 로그인으로 토큰을 발급받을 수 없다")
    void admin_login_rejects_user() {
        given(authRepository.findByUserId("user01")).willReturn(auth("user01"));

        assertThatThrownBy(() -> authService.adminLoginAndCreateToken(new LoginRequest("user01", "password")))
                .isInstanceOf(PasswordIncorrectException.class)
                .hasMessage(ErrorCode.LOGIN_FAILED.getMessage());

        verifyNoInteractions(loginAttemptService, tokenProvider, redisTemplate);
    }

    @Test
    @DisplayName("관리자 전용 로그인은 비밀번호 검증 후 관리자 토큰을 발급한다")
    void admin_login_success() {
        Auth admin = Auth.builder().id(1L).userId("admin").role(UserRole.ROLE_ADMIN).build();
        TokenResponse tokens = new TokenResponse("admin-access", "admin-refresh");
        given(authRepository.findByUserId("admin")).willReturn(admin);
        given(tokenProvider.createToken("admin", "ROLE_ADMIN", 1L)).willReturn(tokens);

        assertThat(authService.adminLoginAndCreateToken(new LoginRequest("Admin", "password"))).isSameAs(tokens);

        then(loginAttemptService).should().validatePassword(1L, "password");
        then(redisTemplate).should().execute(RefreshTokenScripts.SAVE, List.of("RT:admin", "AUTH:STATE:admin"), "admin-refresh", "1209600000", "1:1:ROLE_ADMIN");
    }

    @InjectMocks
    private AuthService authService;

    @Mock
    private AuthRepository authRepository;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Test
    @DisplayName("로그인 성공 시 토큰 생성 및 Refresh Token 저장")
    void login_success_and_return_tokens() {
        LoginRequest info = new LoginRequest("User01", "plain-password");
        Auth auth = auth("user01");
        TokenResponse tokens = new TokenResponse("access-token", "refresh-token");

        given(authRepository.findByUserId("user01")).willReturn(auth);

        given(tokenProvider.createToken("user01", "ROLE_USER", 1L)).willReturn(tokens);

        TokenResponse response = authService.LoginAndCreateToken(info);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        then(authRepository).should().findByUserId("user01");
        then(loginAttemptService).should().validatePassword(1L, "plain-password");
        then(tokenProvider).should().createToken("user01", "ROLE_USER", 1L);
        then(redisTemplate).should().execute(RefreshTokenScripts.SAVE, List.of("RT:user01", "AUTH:STATE:user01"), "refresh-token", "1209600000", "1:1:ROLE_USER");
    }

    @Test
    @DisplayName("로그인 실패 시 토큰을 생성하지 않음")
    void login_fail_with_wrong_password() {
        LoginRequest info = new LoginRequest("user01", "wrong-password");
        Auth auth = auth("user01");

        given(authRepository.findByUserId("user01")).willReturn(auth);
        willThrow(new PasswordIncorrectException("사용자 정보가 일치하지 않습니다.")).given(loginAttemptService).validatePassword(1L, "wrong-password");

        assertThatThrownBy(() -> authService.LoginAndCreateToken(info))
                .isInstanceOf(PasswordIncorrectException.class);

        then(authRepository).should().findByUserId("user01");
        then(loginAttemptService).should().validatePassword(1L, "wrong-password");
        then(tokenProvider).should(never()).createToken(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("Refresh Token 저장 중 Redis 오류 발생 시 예외 발생")
    void login_fail_with_redis_error() {
        LoginRequest info = new LoginRequest("user01", "plain-password");
        Auth auth = auth("user01");
        TokenResponse tokens = new TokenResponse("access-token", "refresh-token");

        given(authRepository.findByUserId("user01")).willReturn(auth);

        given(tokenProvider.createToken("user01", "ROLE_USER", 1L)).willReturn(tokens);
        willThrow(new DataAccessException("redis error") {})
                .given(redisTemplate)
                .execute(RefreshTokenScripts.SAVE, List.of("RT:user01", "AUTH:STATE:user01"), "refresh-token", "1209600000", "1:1:ROLE_USER");

        assertThatThrownBy(() -> authService.LoginAndCreateToken(info))
                .isInstanceOf(RedisException.class);

        then(redisTemplate).should().execute(RefreshTokenScripts.SAVE, List.of("RT:user01", "AUTH:STATE:user01"), "refresh-token", "1209600000", "1:1:ROLE_USER");
    }

    @Test
    @DisplayName("유저 생성 이벤트 처리")
    void insert_user_topic() {
        UserDtoForEvent event = userEvent("user01");

        authService.insertUserTopic(event);

        then(authRepository).should().insert(event);
    }

    @Test
    @DisplayName("유저 수정 이벤트 처리")
    void update_user_topic() {
        UserDtoForEvent event = userEvent("user01");

        authService.updateUserTopic(event);

        then(authRepository).should().update(event);
    }

    @Test
    @DisplayName("유저 삭제 이벤트 처리")
    void delete_user_topic() {
        UserDtoForEvent event = userEvent("user01");

        authService.deleteUserTopic(event);

        then(authRepository).should().delete("user01");
    }

    @Test
    @DisplayName("Refresh Token으로 토큰 재발급 성공")
    void reissue_token_success() {
        String refreshToken = "refresh-token";
        TokenResponse newTokens = new TokenResponse("new-access-token", "new-refresh-token");
        Auth auth = auth("user01");

        given(tokenProvider.validateToken(refreshToken)).willReturn(true);
        given(tokenProvider.getUserId(refreshToken)).willReturn("user01");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("RT:user01")).willReturn(refreshToken);
        given(authRepository.findByUserId("user01")).willReturn(auth);
        given(tokenProvider.createToken("user01", "ROLE_USER", 1L)).willReturn(newTokens);

        given(redisTemplate.execute(RefreshTokenScripts.ROTATE, List.of("RT:user01", "AUTH:STATE:user01"),
                refreshToken, "new-refresh-token", "1209600000", "1:1:ROLE_USER")).willReturn(1L);

        TokenResponse response = authService.reissueToken(refreshToken);

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        then(tokenProvider).should().validateToken(refreshToken);
        then(tokenProvider).should().getUserId(refreshToken);
        then(valueOperations).should().get("RT:user01");
        then(authRepository).should().findByUserId("user01");
        then(tokenProvider).should().createToken("user01", "ROLE_USER", 1L);
        then(redisTemplate).should().execute(RefreshTokenScripts.ROTATE, List.of("RT:user01", "AUTH:STATE:user01"),
                refreshToken, "new-refresh-token", "1209600000", "1:1:ROLE_USER");
    }

    @Test
    @DisplayName("탈퇴한 계정의 Refresh Token은 재발급 거부")
    void reissue_fails_for_withdrawn_user() {
        Auth auth = auth("user01");
        auth.updateInfo(UserDtoForEvent.builder().status(UserStatus.WITHDRAWN.name()).build());
        given(tokenProvider.validateToken("refresh-token")).willReturn(true);
        given(tokenProvider.getUserId("refresh-token")).willReturn("user01");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("RT:user01")).willReturn("refresh-token");
        given(authRepository.findByUserId("user01")).willReturn(auth);

        assertThatThrownBy(() -> authService.reissueToken("refresh-token"))
                .isInstanceOf(RedisException.class);
        then(tokenProvider).should(never()).createToken(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("유효하지 않은 Refresh Token이면 예외 발생")
    void reissue_token_fail_with_invalid_refresh_token() {
        String refreshToken = "invalid-refresh-token";

        given(tokenProvider.validateToken(refreshToken)).willReturn(false);

        assertThatThrownBy(() -> authService.reissueToken(refreshToken))
                .isInstanceOf(RedisException.class);

        then(tokenProvider).should().validateToken(refreshToken);
        then(tokenProvider).should(never()).getUserId(anyString());
    }

    @Test
    @DisplayName("Redis의 Refresh Token과 일치하지 않으면 예외 발생")
    void reissue_token_fail_with_mismatch_refresh_token() {
        String refreshToken = "refresh-token";

        given(tokenProvider.validateToken(refreshToken)).willReturn(true);
        given(tokenProvider.getUserId(refreshToken)).willReturn("user01");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("RT:user01")).willReturn("other-refresh-token");

        assertThatThrownBy(() -> authService.reissueToken(refreshToken))
                .isInstanceOf(RedisException.class);

        then(tokenProvider).should().validateToken(refreshToken);
        then(tokenProvider).should().getUserId(refreshToken);
        then(valueOperations).should().get("RT:user01");
        then(authRepository).should(never()).findByUserId(anyString());
    }

    @Test
    @DisplayName("토큰 재발급 중 유저가 없으면 예외 발생")
    void reissue_token_fail_with_not_exist_user() {
        String refreshToken = "refresh-token";

        given(tokenProvider.validateToken(refreshToken)).willReturn(true);
        given(tokenProvider.getUserId(refreshToken)).willReturn("user01");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("RT:user01")).willReturn(refreshToken);
        given(authRepository.findByUserId("user01")).willThrow(new UserNotExistException("not found"));

        assertThatThrownBy(() -> authService.reissueToken(refreshToken))
                .isInstanceOf(UserNotExistException.class);

        then(authRepository).should().findByUserId("user01");
        then(tokenProvider).should(never()).createToken(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("탈퇴한 계정은 비밀번호가 맞아도 로그인 불가")
    void login_fails_for_withdrawn_user() {
        Auth auth = auth("user01");
        auth.updateInfo(UserDtoForEvent.builder().status(UserStatus.WITHDRAWN.name()).build());
        given(authRepository.findByUserId("user01")).willReturn(auth);


        assertThatThrownBy(() -> authService.LoginAndCreateToken(new LoginRequest("user01", "plain-password")))
                .isInstanceOf(WithdrawnUserException.class)
                .hasMessage("이미 탈퇴한 사용자입니다.");
        then(tokenProvider).should(never()).createToken(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("탈퇴 이벤트를 받으면 Refresh Token 삭제")
    void withdrawal_event_revokes_refresh_token() {
        UserDtoForEvent event = userEvent("user01");
        event.setStatus(UserStatus.WITHDRAWN.name());

        authService.updateUserTopic(event);

        then(authRepository).should().update(event);
        then(tokenStateStore).should().publish("user01", dev.bum.common.security.TokenState.builder().version(2L).active(false).role("ROLE_USER").build());
    }

    @Test
    @DisplayName("로그아웃 성공 시 Redis Refresh Token 삭제")
    void logout_success() {
        String refreshToken = "refresh-token";
        Auth auth = auth("user01");

        given(tokenProvider.validateToken(refreshToken)).willReturn(true);
        given(tokenProvider.getUserId(refreshToken)).willReturn("user01");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("RT:user01")).willReturn(refreshToken);
        given(authRepository.findByUserId("user01")).willReturn(auth);
        given(redisTemplate.execute(RefreshTokenScripts.DELETE, List.of("RT:user01"), refreshToken)).willReturn(1L);

        authService.logout(refreshToken);

        then(tokenProvider).should().validateToken(refreshToken);
        then(tokenProvider).should().getUserId(refreshToken);
        then(valueOperations).should().get("RT:user01");
        then(authRepository).should().findByUserId("user01");
        then(redisTemplate).should().execute(RefreshTokenScripts.DELETE, List.of("RT:user01"), refreshToken);
    }

    @Test
    @DisplayName("로그아웃 시 유효하지 않은 Refresh Token이면 예외 발생")
    void logout_fail_with_invalid_refresh_token() {
        String refreshToken = "invalid-refresh-token";

        given(tokenProvider.validateToken(refreshToken)).willReturn(false);

        assertThatThrownBy(() -> authService.logout(refreshToken))
                .isInstanceOf(RedisException.class);

        then(tokenProvider).should().validateToken(refreshToken);
        then(tokenProvider).should(never()).getUserId(anyString());
        then(redisTemplate).should(never()).execute(eq(RefreshTokenScripts.DELETE), anyList(), anyString());
    }

    @Test
    @DisplayName("로그아웃 시 Redis의 Refresh Token과 일치하지 않으면 예외 발생")
    void logout_fail_with_mismatch_refresh_token() {
        String refreshToken = "refresh-token";

        given(tokenProvider.validateToken(refreshToken)).willReturn(true);
        given(tokenProvider.getUserId(refreshToken)).willReturn("user01");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("RT:user01")).willReturn("other-refresh-token");

        assertThatThrownBy(() -> authService.logout(refreshToken))
                .isInstanceOf(RedisException.class);

        then(tokenProvider).should().validateToken(refreshToken);
        then(tokenProvider).should().getUserId(refreshToken);
        then(valueOperations).should().get("RT:user01");
        then(authRepository).should(never()).findByUserId(anyString());
        then(redisTemplate).should(never()).execute(eq(RefreshTokenScripts.DELETE), anyList(), anyString());
    }

    @Test
    @DisplayName("로그아웃 중 Redis 삭제 오류 발생 시 예외 발생")
    void logout_fail_with_redis_delete_error() {
        String refreshToken = "refresh-token";
        Auth auth = auth("user01");

        given(tokenProvider.validateToken(refreshToken)).willReturn(true);
        given(tokenProvider.getUserId(refreshToken)).willReturn("user01");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("RT:user01")).willReturn(refreshToken);
        given(authRepository.findByUserId("user01")).willReturn(auth);
        willThrow(new DataAccessException("redis error") {})
                .given(redisTemplate)
                .execute(RefreshTokenScripts.DELETE, List.of("RT:user01"), refreshToken);

        assertThatThrownBy(() -> authService.logout(refreshToken))
                .isInstanceOf(RedisException.class);

        then(redisTemplate).should().execute(RefreshTokenScripts.DELETE, List.of("RT:user01"), refreshToken);
    }

    private Auth auth(String userId) {
        return Auth.builder()
                .id(1L)
                .userId(userId)
                .password("encoded-password")
                .role(UserRole.ROLE_USER)
                .build();
    }

    private UserDtoForEvent userEvent(String userId) {
        return UserDtoForEvent.builder()
                .id(1L)
                .userId(userId)
                .password("encoded-password")
                .role("ROLE_USER")
                .build();
    }

    @Test
    @DisplayName("사전 검증 후 다른 요청이 토큰을 교체하면 재발급을 거절한다")
    void reissue_rejects_token_changed_after_precheck() {
        prepareReissue();
        given(redisTemplate.execute(RefreshTokenScripts.ROTATE, List.of("RT:user01", "AUTH:STATE:user01"),
                "refresh-token", "new-refresh-token", "1209600000", "1:1:ROLE_USER")).willReturn(0L);
        assertThatThrownBy(() -> authService.reissueToken("refresh-token"))
                .isInstanceOf(RedisException.class).extracting("errorCode").isEqualTo(ErrorCode.REFRESH_TOKEN_MISMATCH);
    }

    @Test
    @DisplayName("Lua 교체 결과를 확인하지 못하면 새 토큰을 반환하지 않는다")
    void reissue_rejects_unknown_script_result() {
        prepareReissue();
        given(redisTemplate.execute(RefreshTokenScripts.ROTATE, List.of("RT:user01", "AUTH:STATE:user01"),
                "refresh-token", "new-refresh-token", "1209600000", "1:1:ROLE_USER")).willReturn(null);
        assertThatThrownBy(() -> authService.reissueToken("refresh-token"))
                .isInstanceOf(RedisException.class).extracting("errorCode").isEqualTo(ErrorCode.REDIS_ERROR);
    }

    @Test
    @DisplayName("Lua 토큰 교체 중 Redis 장애는 Redis 오류로 전달한다")
    void reissue_wraps_rotation_error() {
        prepareReissue();
        given(redisTemplate.execute(RefreshTokenScripts.ROTATE, List.of("RT:user01", "AUTH:STATE:user01"),
                "refresh-token", "new-refresh-token", "1209600000", "1:1:ROLE_USER"))
                .willThrow(new DataAccessException("Redis 장애") {});
        assertThatThrownBy(() -> authService.reissueToken("refresh-token"))
                .isInstanceOf(RedisException.class).extracting("errorCode").isEqualTo(ErrorCode.REDIS_ERROR);
    }

    @Test
    @DisplayName("사전 검증 후 다른 요청이 토큰을 교체하면 로그아웃 삭제를 거절한다")
    void logout_rejects_token_changed_after_precheck() {
        given(tokenProvider.validateToken("refresh-token")).willReturn(true);
        given(tokenProvider.getUserId("refresh-token")).willReturn("user01");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("RT:user01")).willReturn("refresh-token");
        given(authRepository.findByUserId("user01")).willReturn(auth("user01"));
        given(redisTemplate.execute(RefreshTokenScripts.DELETE, List.of("RT:user01"), "refresh-token")).willReturn(0L);
        assertThatThrownBy(() -> authService.logout("refresh-token"))
                .isInstanceOf(RedisException.class).extracting("errorCode").isEqualTo(ErrorCode.REFRESH_TOKEN_MISMATCH);
        then(redisTemplate).should(never()).delete(anyString());
    }

    @Test
    void reissue_rejects_previous_version_even_if_refresh_key_remains() {
        given(tokenProvider.validateToken("refresh-token")).willReturn(true);
        given(tokenProvider.getUserId("refresh-token")).willReturn("user01");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("RT:user01")).willReturn("refresh-token");
        Auth current = auth("user01");
        current.updateInfo(UserDtoForEvent.builder().password("new-hash").build());
        given(authRepository.findByUserId("user01")).willReturn(current);
        assertThatThrownBy(() -> authService.reissueToken("refresh-token"))
                .isInstanceOf(RedisException.class).extracting("errorCode").isEqualTo(ErrorCode.REFRESH_TOKEN_MISMATCH);
        then(tokenProvider).should(never()).createToken(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    private void prepareReissue() {
        given(tokenProvider.validateToken("refresh-token")).willReturn(true);
        given(tokenProvider.getUserId("refresh-token")).willReturn("user01");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("RT:user01")).willReturn("refresh-token");
        given(authRepository.findByUserId("user01")).willReturn(auth("user01"));
        given(tokenProvider.createToken("user01", "ROLE_USER", 1L))
                .willReturn(new TokenResponse("new-access-token", "new-refresh-token"));
    }
}
