package dev.bum.common.service.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuditLogResponse {

    // 고유 식별자.
    private Long id;

    // 이벤트 발생 시각.
    private LocalDateTime occurredAt;

    // 이벤트가 발생한 서비스 이름.
    private String serviceName;

    // 작업 수행자 유형.
    private String actorType;

    // 작업 수행자 식별자.
    private String actorId;

    // 작업 수행자 이름.
    private String actorName;

    // 수행한 작업.
    private String action;

    // 작업 대상 유형.
    private String targetType;

    // 작업 대상 식별자.
    private String targetId;

    // 처리 결과.
    private String result;

    // 처리 사유.
    private String reason;

    // 요청자의 IP 주소.
    private String ipAddress;

    // 요청 클라이언트 정보.
    private String userAgent;

    // 요청 추적 식별자.
    private String requestId;

    // 분산 추적 식별자.
    private String traceId;

    // 변경 전 데이터.
    private Map<String, Object> beforeData;

    // 변경 후 데이터.
    private Map<String, Object> afterData;

    // 부가 정보.
    private Map<String, Object> metadata;

    // 생성 시각.
    private LocalDateTime createdAt;
}
