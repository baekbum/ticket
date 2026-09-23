package dev.bum.auth_service.service;

import dev.bum.auth_service.exception.BlacklistedUserException;
import dev.bum.auth_service.jpa.Auth;
import dev.bum.auth_service.jpa.AuthRepository;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.kafka.user.UserDtoForEvent;
import dev.bum.common.service.auth.dto.LoginRequest;
import dev.bum.common.service.user.user.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthBlacklistTest {
    @InjectMocks AuthService service;
    @Mock AuthRepository repository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider tokenProvider;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    @Test
    void event_updates_blacklist() {
        Auth auth = new Auth(1L, "user", "encoded", UserRole.ROLE_USER);
        LocalDate until = LocalDate.of(2026, 12, 31);
        auth.updateInfo(UserDtoForEvent.builder().isBlacklisted(true).blacklistedUntil(until).build());
        assertThat(auth.isCurrentlyBlacklisted()).isTrue();
        assertThat(auth.getBlacklistedUntil()).isEqualTo(until);

        auth.updateInfo(UserDtoForEvent.builder().isBlacklisted(true).build());
        assertThat(auth.getBlacklistedUntil()).isNull();

        auth.updateInfo(UserDtoForEvent.builder().isBlacklisted(false).build());
        assertThat(auth.isCurrentlyBlacklisted()).isFalse();
        assertThat(auth.getBlacklistedUntil()).isNull();
    }

    @Test
    void login_rejects_blacklisted_user_before_issuing_token() {
        Auth auth = new Auth(UserDtoForEvent.builder().id(1L).userId("user")
                .password("encoded").role("ROLE_USER").isBlacklisted(true)
                .blacklistedUntil(LocalDate.of(2026, 12, 31)).build());
        given(repository.findByUserId("user")).willReturn(auth);
        given(passwordEncoder.matches("password", "encoded")).willReturn(true);

        assertThatThrownBy(() -> service.LoginAndCreateToken(new LoginRequest("user", "password")))
                .isInstanceOf(BlacklistedUserException.class)
                .extracting("blacklistedUntil").isEqualTo(LocalDate.of(2026, 12, 31));
        verify(tokenProvider, never()).createToken("user", "ROLE_USER");
    }

    @Test
    void reissue_rejects_blacklisted_user_before_issuing_token() {
        Auth auth = new Auth(UserDtoForEvent.builder().id(1L).userId("user")
                .password("encoded").role("ROLE_USER").isBlacklisted(true).build());
        given(tokenProvider.validateToken("refresh")).willReturn(true);
        given(tokenProvider.getUserId("refresh")).willReturn("user");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("RT:user")).willReturn("refresh");
        given(repository.findByUserId("user")).willReturn(auth);

        assertThatThrownBy(() -> service.reissueToken("refresh"))
                .isInstanceOf(BlacklistedUserException.class);
        verify(tokenProvider, never()).createToken("user", "ROLE_USER");
    }
}
