package dev.bum.common.service.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuditLogCondRequest {

    private LocalDateTime occurredFrom;
    private LocalDateTime occurredTo;
    private String serviceName;
    private String actorType;
    private String actorId;
    private String actorName;
    private String action;
    private String targetType;
    private String targetId;
    private String result;
    private String requestId;
    private String traceId;

    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer size = 20;

    private List<String> sort;
}
