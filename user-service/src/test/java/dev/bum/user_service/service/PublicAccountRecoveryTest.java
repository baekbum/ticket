package dev.bum.user_service.service;

import dev.bum.common.kafka.user.UserDtoForEvent;
import dev.bum.common.service.user.user.dto.*;
import dev.bum.common.service.user.user.enums.UserRole;
import dev.bum.user_service.exception.UserNotExistException;
import dev.bum.user_service.jpa.user.User;
import dev.bum.user_service.jpa.user.UserRepository;
import dev.bum.user_service.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicAccountRecoveryTest {
    @InjectMocks private UserService service;
    @Mock private UserRepository repository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private KafkaTemplate<String, UserDtoForEvent> kafkaTemplate;

    @ParameterizedTest
    @ValueSource(strings = {"ID_PHONE", "ID_EMAIL", "PASSWORD_PHONE", "PASSWORD_EMAIL"})
    void public_recovery_rejects_admin_without_disclosing_role(String route) {
        User admin = User.builder().id(1L).userId("admin").role(UserRole.ROLE_ADMIN).build();
        prepareLookup(route, admin);

        assertThatThrownBy(() -> recover(route)).isInstanceOf(UserNotExistException.class)
                .hasMessage("사용자 정보가 일치하지 않습니다.");
        verifyNoInteractions(passwordEncoder, kafkaTemplate);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ID_PHONE", "ID_EMAIL", "PASSWORD_PHONE", "PASSWORD_EMAIL"})
    void public_recovery_still_allows_regular_members(String route) {
        User user = User.builder().id(1L).userId("member01").role(UserRole.ROLE_USER).build();
        prepareLookup(route, user);

        Object response = recover(route);
        if (response instanceof FindPasswordResponse passwordResponse) {
            assertThat(passwordResponse.getResetToken()).isNotBlank();
        } else {
            assertThat(((FindUserIdResponse) response).getMaskedUserId()).isEqualTo("memb****");
        }
    }

    @Test
    void reset_rechecks_role_after_token_issuance() {
        User user = User.builder().id(1L).userId("member01").role(UserRole.ROLE_USER).build();
        prepareLookup("PASSWORD_EMAIL", user);
        FindPasswordResponse response = (FindPasswordResponse) recover("PASSWORD_EMAIL");
        user.updateInfo(UpdateUserRequest.builder().role("ROLE_ADMIN").build());
        when(repository.selectById("member01")).thenReturn(user);

        assertThatThrownBy(() -> service.resetPassword(ResetPasswordRequest.builder()
                .resetToken(response.getResetToken()).password("new-password").build()))
                .isInstanceOf(UserNotExistException.class);

        verify(repository, never()).update(anyString(), any());
        verifyNoInteractions(passwordEncoder, kafkaTemplate);
    }

    @Test
    void regular_member_can_reset_password_with_issued_token() {
        User user = User.builder().id(1L).userId("member01").role(UserRole.ROLE_USER).build();
        prepareLookup("PASSWORD_EMAIL", user);
        FindPasswordResponse response = (FindPasswordResponse) recover("PASSWORD_EMAIL");
        when(repository.selectById("member01")).thenReturn(user);
        when(repository.update(eq("member01"), any())).thenReturn(user);
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        service.resetPassword(ResetPasswordRequest.builder()
                .resetToken(response.getResetToken()).password("new-password").build());

        verify(repository).update(eq("member01"), argThat(request -> "new-password".equals(request.getPassword())));
        verify(kafkaTemplate).send(any(), eq("member01"), argThat(event -> Boolean.TRUE.equals(event.getPasswordReset())));
    }

    @Test
    void public_password_validation_rejects_admin_even_with_stale_user_role() {
        when(repository.selectById("admin")).thenReturn(User.builder().userId("admin").role(UserRole.ROLE_ADMIN).build());

        assertThatThrownBy(() -> service.validateMyPassword("admin", "password"))
                .isInstanceOf(UserNotExistException.class);
        verifyNoInteractions(passwordEncoder);
    }

    private void prepareLookup(String route, User user) {
        switch (route) {
            case "ID_PHONE" -> when(repository.selectByNameAndPhoneNumber("name", "01012345678")).thenReturn(user);
            case "ID_EMAIL" -> when(repository.selectByNameAndEmail("name", "member@example.com")).thenReturn(user);
            case "PASSWORD_PHONE" -> when(repository.selectByUserIdAndNameAndPhoneNumber("member01", "name", "01012345678")).thenReturn(user);
            case "PASSWORD_EMAIL" -> when(repository.selectByUserIdAndNameAndEmail("member01", "name", "member@example.com")).thenReturn(user);
            default -> throw new IllegalArgumentException(route);
        }
    }

    private Object recover(String route) {
        FindUserIdRequest id = FindUserIdRequest.builder().name("name").phoneNumber("01012345678").email("member@example.com").build();
        FindPasswordRequest password = FindPasswordRequest.builder().userId("member01").name("name")
                .phoneNumber("01012345678").email("member@example.com").build();
        return switch (route) {
            case "ID_PHONE" -> service.findUserIdByPhoneNumber(id);
            case "ID_EMAIL" -> service.findUserIdByEmail(id);
            case "PASSWORD_PHONE" -> service.findPasswordByPhoneNumber(password);
            case "PASSWORD_EMAIL" -> service.findPasswordByEmail(password);
            default -> throw new IllegalArgumentException(route);
        };
    }
}
