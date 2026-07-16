package dev.bum.auth_service.service;

import dev.bum.auth_service.exception.PasswordIncorrectException;
import dev.bum.auth_service.exception.RedisException;
import dev.bum.auth_service.exception.UserNotExistException;
import dev.bum.auth_service.jpa.Auth;
import dev.bum.auth_service.jpa.AuthRepository;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.jwt.dto.TokenResponse;
import dev.bum.common.kafka.user.UserDtoForEvent;
import dev.bum.common.service.auth.dto.LoginRequest;
import dev.bum.common.service.user.user.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

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
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("userId로 Auth 조회")
    void find_by_user_id() {
        Auth auth = auth("user01");

        given(authRepository.findByUserId("user01")).willReturn(auth);

        Auth response = authService.findByUserId("user01");

        assertThat(response.getUserId()).isEqualTo("user01");
        then(authRepository).should().findByUserId("user01");
    }

    @Test
    @DisplayName("비밀번호 검증 성공")
    void validate_info_success() {
        LoginRequest info = new LoginRequest("user01", "plain-password");
        Auth auth = auth("user01");

        given(authRepository.findByUserId("user01")).willReturn(auth);
        given(passwordEncoder.matches("plain-password", "encoded-password")).willReturn(true);

        authService.validateInfo(info);

        then(authRepository).should().findByUserId("user01");
        then(passwordEncoder).should().matches("plain-password", "encoded-password");
    }

    @Test
    @DisplayName("비밀번호 검증 실패")
    void validate_info_fail_with_wrong_password() {
        LoginRequest info = new LoginRequest("user01", "wrong-password");
        Auth auth = auth("user01");

        given(authRepository.findByUserId("user01")).willReturn(auth);
        given(passwordEncoder.matches("wrong-password", "encoded-password")).willReturn(false);

        assertThatThrownBy(() -> authService.validateInfo(info))
                .isInstanceOf(PasswordIncorrectException.class);

        then(authRepository).should().findByUserId("user01");
        then(passwordEncoder).should().matches("wrong-password", "encoded-password");
    }

    @Test
    @DisplayName("로그인 성공 시 토큰 생성 및 Refresh Token 저장")
    void login_success_and_return_tokens() {
        LoginRequest info = new LoginRequest("user01", "plain-password");
        Auth auth = auth("user01");
        TokenResponse tokens = new TokenResponse("access-token", "refresh-token");

        given(authRepository.findByUserId("user01")).willReturn(auth);
        given(passwordEncoder.matches("plain-password", "encoded-password")).willReturn(true);
        given(tokenProvider.createToken("user01", "ROLE_USER")).willReturn(tokens);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        TokenResponse response = authService.LoginAndCreateToken(info);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        then(authRepository).should().findByUserId("user01");
        then(passwordEncoder).should().matches("plain-password", "encoded-password");
        then(tokenProvider).should().createToken("user01", "ROLE_USER");
        then(valueOperations).should().set("RT:user01", "refresh-token", Duration.ofDays(14));
    }

    @Test
    @DisplayName("로그인 실패 시 토큰을 생성하지 않음")
    void login_fail_with_wrong_password() {
        LoginRequest info = new LoginRequest("user01", "wrong-password");
        Auth auth = auth("user01");

        given(authRepository.findByUserId("user01")).willReturn(auth);
        given(passwordEncoder.matches("wrong-password", "encoded-password")).willReturn(false);

        assertThatThrownBy(() -> authService.LoginAndCreateToken(info))
                .isInstanceOf(PasswordIncorrectException.class);

        then(authRepository).should().findByUserId("user01");
        then(passwordEncoder).should().matches("wrong-password", "encoded-password");
        then(tokenProvider).should(never()).createToken(anyString(), anyString());
    }

    @Test
    @DisplayName("Refresh Token 저장 중 Redis 오류 발생 시 예외 발생")
    void login_fail_with_redis_error() {
        LoginRequest info = new LoginRequest("user01", "plain-password");
        Auth auth = auth("user01");
        TokenResponse tokens = new TokenResponse("access-token", "refresh-token");

        given(authRepository.findByUserId("user01")).willReturn(auth);
        given(passwordEncoder.matches("plain-password", "encoded-password")).willReturn(true);
        given(tokenProvider.createToken("user01", "ROLE_USER")).willReturn(tokens);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        willThrow(new DataAccessException("redis error") {})
                .given(valueOperations)
                .set("RT:user01", "refresh-token", Duration.ofDays(14));

        assertThatThrownBy(() -> authService.LoginAndCreateToken(info))
                .isInstanceOf(RedisException.class);

        then(valueOperations).should().set("RT:user01", "refresh-token", Duration.ofDays(14));
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
        given(tokenProvider.createToken("user01", "ROLE_USER")).willReturn(newTokens);

        TokenResponse response = authService.reissueToken(refreshToken);

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        then(tokenProvider).should().validateToken(refreshToken);
        then(tokenProvider).should().getUserId(refreshToken);
        then(valueOperations).should().get("RT:user01");
        then(authRepository).should().findByUserId("user01");
        then(tokenProvider).should().createToken("user01", "ROLE_USER");
        then(valueOperations).should().set("RT:user01", "new-refresh-token", Duration.ofDays(14));
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
        then(tokenProvider).should(never()).createToken(anyString(), anyString());
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
}
