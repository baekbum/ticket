package dev.bum.admin_service.monitoring;

import java.util.List;

public record FailureMetricResponse(
        String key,
        String name,
        String description,
        String unit,
        Double value,
        Double warningThreshold,
        Double criticalThreshold,
        FailureMetricLevel level,
        String promql,
        List<FailureMetricDetailResponse> details
) {
}
