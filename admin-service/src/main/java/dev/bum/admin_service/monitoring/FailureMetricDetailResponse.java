package dev.bum.admin_service.monitoring;

import java.util.Map;

public record FailureMetricDetailResponse(
        String name,
        String job,
        String instance,
        Double value,
        Map<String, String> labels
) {
}
