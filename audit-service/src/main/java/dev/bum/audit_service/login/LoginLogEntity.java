package dev.bum.audit_service.login;

import dev.bum.common.kafka.login.LoginLogEvent;
import dev.bum.common.service.audit.dto.LoginLogResponse;
import dev.bum.common.service.audit.dto.MyLoginLogResponse;
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

    // 고유 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Kafka 재전달 시 중복 저장을 방지하기 위한 이벤트 식별자.
    @Column(nullable = false, length = 36)
    private String eventId;

    // 인증 데이터의 PK. 존재하지 않는 계정의 로그인 실패도 기록하기 위해 nullable이다.
    private Long authId;

    // 로그인 시도에 사용된 식별자. 비밀번호나 토큰은 저장하지 않는다.
    @Column(nullable = false, length = 100)
    private String loginId;

    // 처리 결과.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoginResult result;

    // 로그인 인증 방식.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoginAuthMethod authMethod;

    // 실패 사유.
    @Column(length = 100)
    private String failureReason;

    // 요청자의 IP 주소.
    @Column(length = 45)
    private String ipAddress;

    // 요청 클라이언트 정보.
    @Column(length = 500)
    private String userAgent;

    // 세션 식별자.
    @Column(length = 100)
    private String sessionId;

    // 요청 추적 식별자.
    @Column(length = 100)
    private String requestId;

    // 분산 추적 식별자.
    @Column(length = 100)
    private String traceId;

    // 이벤트 발생 시각.
    @Column(nullable = false)
    private LocalDateTime occurredAt;

    // 생성 시각.
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 전달받은 값으로 로그인 이력 객체를 초기화한다.
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

    // 원본 데이터를 로그인 이력 객체로 변환한다.
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

    // 외부 응답에 사용할 DTO로 변환한다.
    public LoginLogResponse toResponse() {

        return LoginLogResponse.builder()
                .id(id)
                .eventId(eventId)
                .authId(authId)
                .loginId(loginId)
                .result(result.name())
                .authMethod(authMethod.name())
                .failureReason(failureReason)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .sessionId(sessionId)
                .requestId(requestId)
                .traceId(traceId)
                .occurredAt(occurredAt)
                .createdAt(createdAt)
                .build();
    }

    // 현재 사용자에게 공개할 정보만 포함한 응답으로 변환한다.
    public MyLoginLogResponse toMyResponse() {

        return MyLoginLogResponse.builder()
                .id(id)
                .result(result.name())
                .authMethod(authMethod.name())
                .failureReason(failureReason)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .occurredAt(occurredAt)
                .build();
    }
}
