package dev.bum.admin_service.monitoring;

import java.util.List;

record FailureMetricDefinition(
        String key,
        String name,
        String description,
        String unit,
        String promqlTemplate,
        double defaultWarningThreshold,
        double defaultCriticalThreshold
) {
    String promql(String range) {
        return promqlTemplate.replace("{range}", range);
    }

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
