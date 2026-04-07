package dev.bum.auth_service.service;

import dev.bum.auth_service.exception.UserNotExistException;
import dev.bum.auth_service.jpa.Auth;
import dev.bum.auth_service.jpa.AuthRepository;
import dev.bum.auth_service.vo.LoginInfo;
import dev.bum.common.dto.TokenDto;
import dev.bum.common.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private AuthRepository authRepository;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private StringRedisTemplate redisTemplate;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("로그인 성공: 올바른 정보를 입력하면 토큰을 반환한다")
    void login_success_and_return_tokens() {
        String userId = "user01";
        String password = "user123!";
        String role = "ROLE_USER";

        Auth auth = Auth.builder()
                .userId(userId)
                .password(password)
                .role(role)
                .build();

        TokenDto mockTokens = new TokenDto("access-token", "refresh-token");

        given(authRepository.findByUserId(userId)).willReturn(auth);
        given(passwordEncoder.matches(password, auth.getPassword())).willReturn(true);
        given(tokenProvider.createToken(anyString(), anyString())).willReturn(mockTokens);

        TokenDto response = authService.LoginAndCreateToken(new LoginInfo(userId, password));

        assertThat(response.getAccessToken()).isNotNull();
        verify(authRepository).findByUserId(userId);
        verify(redisTemplate.opsForValue()).set(
                eq("RT:" + userId),
                anyString(),
                any(Duration.class)
        );
    }

    @Test
    @DisplayName("로그인 실패: 존재하지 않는 유저인 경우 예외가 발생한다")
    void login_fail() {
        String userId = "userNotExist";
        String password = "user123!";

        given(authRepository.findByUserId(userId)).willThrow(new UserNotExistException("해당 유저를 발견하지 못했습니다."));

        assertThatThrownBy(() -> authService.LoginAndCreateToken(new LoginInfo(userId, password)))
                .isInstanceOf(UserNotExistException.class)
                .hasMessageContaining("해당 유저를 발견하지 못했습니다.");
    }
}