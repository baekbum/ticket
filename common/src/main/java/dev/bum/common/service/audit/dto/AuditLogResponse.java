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

    private Long id;
    private LocalDateTime occurredAt;
    private String serviceName;
    private String actorType;
    private String actorId;
    private String actorName;
    private String action;
    private String targetType;
    private String targetId;
    private String result;
    private String reason;
    private String ipAddress;
    private String userAgent;
    private String requestId;
    private String traceId;
    private Map<String, Object> beforeData;
    private Map<String, Object> afterData;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
}
