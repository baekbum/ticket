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
public class LoginLogCondRequest {

    // 발생 시각 조회 시작 조건.
    private LocalDateTime occurredFrom;

    // 발생 시각 조회 종료 조건.
    private LocalDateTime occurredTo;

    // 인증 정보 식별자.
    private Long authId;

    // 로그인 ID.
    private String loginId;

    // 처리 결과.
    private String result;

    // 로그인 인증 방식.
    private String authMethod;

    // 요청자의 IP 주소.
    private String ipAddress;

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
