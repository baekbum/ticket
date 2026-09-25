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

    private String eventId;
    private Long authId;
    private String loginId;
    private String result;
    private String authMethod;
    private String failureReason;
    private String ipAddress;
    private String userAgent;
    private String sessionId;
    private String requestId;
    private String traceId;
    private LocalDateTime occurredAt;
}
