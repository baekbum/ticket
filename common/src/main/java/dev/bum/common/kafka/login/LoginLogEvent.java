package dev.bum.common.kafka.login;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginLogEvent {

    // 공연 식별자.
    private String eventId;

    // 인증 정보 식별자.
    private Long authId;

    // 로그인 ID.
    private String loginId;

    // 처리 결과.
    private String result;

    // 로그인 인증 방식.
    private String authMethod;

    // 실패 사유.
    private String failureReason;

    // 요청자의 IP 주소.
    private String ipAddress;

    // 요청 클라이언트 정보.
    private String userAgent;

    // 세션 식별자.
    private String sessionId;

    // 요청 추적 식별자.
    private String requestId;

    // 분산 추적 식별자.
    private String traceId;

    // 이벤트 발생 시각.
    private LocalDateTime occurredAt;
}
