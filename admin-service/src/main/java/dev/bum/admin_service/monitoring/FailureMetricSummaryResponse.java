package dev.bum.admin_service.monitoring;

import java.time.OffsetDateTime;
import java.util.List;

public record FailureMetricSummaryResponse(
        OffsetDateTime collectedAt,
        String range,
        List<FailureMetricResponse> metrics
) {
}
