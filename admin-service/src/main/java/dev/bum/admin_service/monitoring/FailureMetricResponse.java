package dev.bum.admin_service.monitoring;

import java.util.List;

public record FailureMetricResponse(
        // 데이터 식별 키.
        String key,

        // 이름.
        String name,

        // 상세 설명.
        String description,

        // 메트릭 측정 단위.
        String unit,

        // 데이터 값.
        Double value,

        // 경고 판단 기준값.
        Double warningThreshold,

        // 위험 판단 기준값.
        Double criticalThreshold,

        // 장애 심각도.
        FailureMetricLevel level,

        // 메트릭 조회용 PromQL.
        String promql,

        // 상세 정보 목록.
        List<FailureMetricDetailResponse> details
) {
}
