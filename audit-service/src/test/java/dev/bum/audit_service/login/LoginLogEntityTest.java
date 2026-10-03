package dev.bum.audit_service.login;

import dev.bum.common.kafka.login.LoginLogEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LoginLogEntityTest {

    @Test
    @DisplayName("로그인 이벤트를 엔티티로 변환")
    void create_from_event() {
        LoginLogEvent event = LoginLogEvent.builder()
                .eventId("550e8400-e29b-41d4-a716-446655440002")
                .authId(1L)
                .loginId("user01")
                .result("FAILURE")
                .authMethod("PASSWORD")
                .failureReason("PasswordIncorrectException")
                .ipAddress("127.0.0.1")
                .occurredAt(LocalDateTime.of(2026, 9, 25, 21, 0))
                .build();

        LoginLogEntity loginLog = LoginLogEntity.from(event);

        assertThat(loginLog.getEventId()).isEqualTo(event.getEventId());
        assertThat(loginLog.getResult()).isEqualTo(LoginResult.FAILURE);
        assertThat(loginLog.getAuthMethod()).isEqualTo(LoginAuthMethod.PASSWORD);
        assertThat(loginLog.getFailureReason()).isEqualTo("PasswordIncorrectException");
    }

    @Test
    @DisplayName("로그인 로그 생성 시 인증 방식과 시간이 기본값으로 설정된다")
    void create_with_defaults() {
        LoginLogEntity loginLog = LoginLogEntity.builder()
                .eventId("550e8400-e29b-41d4-a716-446655440000")
                .loginId("user01")
                .result(LoginResult.FAILURE)
                .failureReason("BAD_CREDENTIALS")
                .ipAddress("127.0.0.1")
                .build();

        assertThat(loginLog.getAuthMethod()).isEqualTo(LoginAuthMethod.PASSWORD);
        assertThat(loginLog.getOccurredAt()).isNotNull();
        assertThat(loginLog.getCreatedAt()).isNotNull();
        assertThat(loginLog.getAuthId()).isNull();
    }

    @Test
    @DisplayName("로그인 로그의 이벤트 정보를 보존한다")
    void create_with_event_values() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 25, 20, 30);

        LoginLogEntity loginLog = LoginLogEntity.builder()
                .eventId("550e8400-e29b-41d4-a716-446655440001")
                .authId(1L)
                .loginId("user01")
                .result(LoginResult.SUCCESS)
                .authMethod(LoginAuthMethod.PASSWORD)
                .sessionId("session-1")
                .requestId("request-1")
                .traceId("trace-1")
                .occurredAt(occurredAt)
                .build();

        assertThat(loginLog.getAuthId()).isEqualTo(1L);
        assertThat(loginLog.getResult()).isEqualTo(LoginResult.SUCCESS);
        assertThat(loginLog.getAuthMethod()).isEqualTo(LoginAuthMethod.PASSWORD);
        assertThat(loginLog.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(loginLog.getSessionId()).isEqualTo("session-1");
        assertThat(loginLog.getRequestId()).isEqualTo("request-1");
        assertThat(loginLog.getTraceId()).isEqualTo("trace-1");
    }
}
