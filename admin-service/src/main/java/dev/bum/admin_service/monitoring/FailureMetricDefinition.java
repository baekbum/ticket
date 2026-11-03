package dev.bum.admin_service.monitoring;

import java.util.List;

record FailureMetricDefinition(
        // 데이터 식별 키.
        String key,

        // 이름.
        String name,

        // 상세 설명.
        String description,

        // 메트릭 측정 단위.
        String unit,

        // 조회 시간 범위를 적용할 PromQL 템플릿.
        String promqlTemplate,

        // 기본 경고 판단 기준값.
        double defaultWarningThreshold,

        // 기본 위험 판단 기준값.
        double defaultCriticalThreshold
) {

    // 조회 범위를 적용한 메트릭 조회용 PromQL을 구성한다.
    String promql(String range) {

        return promqlTemplate.replace("{range}", range);
    }

    // 외부 응답에 사용할 DTO로 변환한다.
    FailureMetricResponse toResponse(
            String range,
            Double value,
            FailureMetricProperties.Threshold threshold,
            List<FailureMetricDetailResponse> details
    ) {

        return new FailureMetricResponse(
                key,
                name,
                description,
                unit,
                value,
                threshold.getWarningThreshold(),
                threshold.getCriticalThreshold(),
                levelOf(value, threshold),
                promql(range),
                details
        );
    }

    // 측정값과 임계값을 비교해 장애 심각도를 결정한다.
    private FailureMetricLevel levelOf(Double value, FailureMetricProperties.Threshold threshold) {

        if (value == null || value.isNaN() || value.isInfinite()) {
            return FailureMetricLevel.UNKNOWN;
        }

        if (value >= threshold.getCriticalThreshold()) {
            return FailureMetricLevel.CRITICAL;
        }

        if (value >= threshold.getWarningThreshold()) {
            return FailureMetricLevel.WARNING;
        }
        return FailureMetricLevel.NORMAL;
    }
}
