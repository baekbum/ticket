package dev.bum.common.service.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MyLoginLogResponse {

    // 고유 식별자.
    private Long id;

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

    // 이벤트 발생 시각.
    private LocalDateTime occurredAt;
}
