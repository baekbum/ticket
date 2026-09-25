package dev.bum.audit_service.login;

import dev.bum.common.kafka.login.LoginLogEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "login_log",
        uniqueConstraints = @UniqueConstraint(name = "uk_login_log_event_id", columnNames = "event_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Kafka 재전달 시 중복 저장을 방지하기 위한 이벤트 식별자.
     */
    @Column(nullable = false, length = 36)
    private String eventId;

    /**
     * 인증 데이터의 PK. 존재하지 않는 계정의 로그인 실패도 기록하기 위해 nullable이다.
     */
    private Long authId;

    /**
     * 로그인 시도에 사용된 식별자. 비밀번호나 토큰은 저장하지 않는다.
     */
    @Column(nullable = false, length = 100)
    private String loginId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoginResult result;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoginAuthMethod authMethod;

    @Column(length = 100)
    private String failureReason;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 500)
    private String userAgent;

    @Column(length = 100)
    private String sessionId;

    @Column(length = 100)
    private String requestId;

    @Column(length = 100)
    private String traceId;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public LoginLogEntity(
            String eventId,
            Long authId,
            String loginId,
            LoginResult result,
            LoginAuthMethod authMethod,
            String failureReason,
            String ipAddress,
            String userAgent,
            String sessionId,
            String requestId,
            String traceId,
            LocalDateTime occurredAt
    ) {
        LocalDateTime now = LocalDateTime.now();
        this.eventId = eventId;
        this.authId = authId;
        this.loginId = loginId;
        this.result = result;
        this.authMethod = authMethod != null ? authMethod : LoginAuthMethod.PASSWORD;
        this.failureReason = failureReason;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.traceId = traceId;
        this.occurredAt = occurredAt != null ? occurredAt : now;
        this.createdAt = now;
    }

    public static LoginLogEntity from(LoginLogEvent event) {
        return LoginLogEntity.builder()
                .eventId(event.getEventId())
                .authId(event.getAuthId())
                .loginId(event.getLoginId())
                .result(LoginResult.valueOf(event.getResult()))
                .authMethod(LoginAuthMethod.valueOf(event.getAuthMethod()))
                .failureReason(event.getFailureReason())
                .ipAddress(event.getIpAddress())
                .userAgent(event.getUserAgent())
                .sessionId(event.getSessionId())
                .requestId(event.getRequestId())
                .traceId(event.getTraceId())
                .occurredAt(event.getOccurredAt())
                .build();
    }
}
