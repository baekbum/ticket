package dev.bum.audit_service.kafka;

import dev.bum.audit_service.login.LoginAuthMethod;
import dev.bum.audit_service.login.LoginLogEntity;
import dev.bum.audit_service.login.LoginLogPersistenceService;
import dev.bum.audit_service.login.LoginResult;
import dev.bum.common.kafka.login.LoginLogEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class LoginLogConsumerTest {

    private final LoginLogPersistenceService persistenceService = mock(LoginLogPersistenceService.class);
    private final LoginLogConsumer consumer = new LoginLogConsumer(persistenceService);

    @Test
    @DisplayName("로그인 로그 이벤트를 저장 서비스로 위임")
    void consume_saves_login_log_event() {
        LoginLogEvent event = loginLogEvent();
        given(persistenceService.save(event)).willReturn(loginLogEntity());

        consumer.consume(event);

        then(persistenceService).should().save(event);
    }

    @Test
    @DisplayName("이미 저장된 eventId는 정상 처리")
    void consume_ignores_duplicate_event() {
        LoginLogEvent event = loginLogEvent();
        given(persistenceService.save(event)).willThrow(new DataIntegrityViolationException(
                "duplicate eventId",
                new SQLException("unique violation", "23505")
        ));

        assertThatCode(() -> consumer.consume(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("중복이 아닌 DB 오류는 Kafka error handler로 전파")
    void consume_propagates_non_duplicate_database_error() {
        LoginLogEvent event = loginLogEvent();
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "not-null violation",
                new SQLException("not-null violation", "23502")
        );
        given(persistenceService.save(event)).willThrow(exception);

        assertThatThrownBy(() -> consumer.consume(event)).isSameAs(exception);
    }

    private LoginLogEvent loginLogEvent() {
        return LoginLogEvent.builder()
                .eventId("550e8400-e29b-41d4-a716-446655440000")
                .authId(1L)
                .loginId("user01")
                .result("SUCCESS")
                .authMethod("PASSWORD")
                .occurredAt(LocalDateTime.now())
                .build();
    }

    private LoginLogEntity loginLogEntity() {
        return LoginLogEntity.builder()
                .eventId("550e8400-e29b-41d4-a716-446655440000")
                .authId(1L)
                .loginId("user01")
                .result(LoginResult.SUCCESS)
                .authMethod(LoginAuthMethod.PASSWORD)
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
