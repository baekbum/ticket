package dev.bum.user_service.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.bum.common.kafka.user.UserDtoForEvent;
import dev.bum.common.service.user.user.dto.*;
import dev.bum.common.service.user.user.enums.UserRole;
import dev.bum.user_service.audit.AuditContext;
import dev.bum.user_service.exception.PasswordIncorrectException;
import dev.bum.user_service.jpa.user.User;
import dev.bum.user_service.jpa.user.UserRepository;
import dev.bum.user_service.service.user.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserSensitiveLoggingTest {
    private static final String PASSWORD = "plaintext-password-marker!";
    private static final String HASH = "encoded-password-marker";
    @InjectMocks private UserService service;
    @Mock private UserRepository repository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private KafkaTemplate<String, UserDtoForEvent> kafkaTemplate;

    private Logger logger;
    private Level originalLevel;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void captureLogs() {
        ReflectionTestUtils.setField(service, "userTopic", "user-events");
        logger = (Logger) LoggerFactory.getLogger(UserService.class);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void cleanup() {
        logger.detachAppender(logs);
        logger.setLevel(originalLevel);
        logs.stop();
        AuditContext.clear();
    }

    @Test
    void signup_records_account_id_without_password_or_request_details() {
        InsertUserRequest request = InsertUserRequest.builder().userId("member").password(PASSWORD)
                .name("private-name-marker").email("private-email-marker@example.com")
                .phoneNumber("private-phone-marker").address("private-address-marker").build();
        when(repository.insert(request)).thenAnswer(invocation -> {
            request.setPassword(HASH);
            return user();
        });
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        service.insert(request);

        assertSafeLogs("[INSERT] userId=member");
    }

    @Test
    void update_records_account_id_and_audits_only_password_change_status() {
        UpdateUserRequest request = UpdateUserRequest.builder().password(PASSWORD)
                .email("private-email-marker@example.com").address("private-address-marker").build();
        when(repository.selectById("member")).thenReturn(user());
        when(repository.update("member", request)).thenReturn(user());

        service.update("member", request);

        assertSafeLogs("[UPDATE] userId=member");
        assertThat(AuditContext.getBeforeData()).containsEntry("password", "UNCHANGED");
        assertThat(AuditContext.getAfterData()).containsEntry("password", "CHANGED");
    }

    @Test
    void password_validation_success_does_not_log_password() {
        when(repository.selectById("member")).thenReturn(user());
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

        service.validateInfo(ValidatePasswordRequest.builder().userId("member").password(PASSWORD).build());

        assertSafeLogs("[VALIDATE] userId=member");
    }

    @Test
    void password_validation_failure_does_not_log_attempted_password() {
        when(repository.selectById("member")).thenReturn(user());

        assertThatThrownBy(() -> service.validateInfo(ValidatePasswordRequest.builder()
                .userId("member").password(PASSWORD).build())).isInstanceOf(PasswordIncorrectException.class);

        assertSafeLogs("[VALIDATE] userId=member");
    }

    private void assertSafeLogs(String expected) {
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).contains(expected);
        for (ILoggingEvent event : logs.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(PASSWORD, HASH, "private-name-marker",
                    "private-email-marker", "private-phone-marker", "private-address-marker");
            if (event.getArgumentArray() != null) {
                for (Object argument : event.getArgumentArray()) {
                    assertThat(argument).isNotInstanceOf(InsertUserRequest.class)
                            .isNotInstanceOf(UpdateUserRequest.class).isNotInstanceOf(ValidatePasswordRequest.class);
                    assertThat(String.valueOf(argument)).doesNotContain(PASSWORD, HASH);
                }
            }
        }
    }

    private User user() {
        return User.builder().id(1L).userId("member").role(UserRole.ROLE_USER).password(HASH).build();
    }
}
