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

    // 발생 시각 조회 시작 조건.
    private LocalDateTime occurredFrom;

    // 발생 시각 조회 종료 조건.
    private LocalDateTime occurredTo;

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

    // 요청 추적 식별자.
    private String requestId;

    // 분산 추적 식별자.
    private String traceId;

    // 조회할 페이지.
    @Builder.Default
    private Integer page = 0;

    // 페이지당 조회 건수.
    @Builder.Default
    private Integer size = 20;

    // 조회 결과 정렬 조건.
    private List<String> sort;
}
