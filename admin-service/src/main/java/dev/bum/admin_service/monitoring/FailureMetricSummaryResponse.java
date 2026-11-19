package dev.bum.admin_service.monitoring;

import java.time.OffsetDateTime;
import java.util.List;

public record FailureMetricSummaryResponse(
        // 메트릭 수집 시각.
        OffsetDateTime collectedAt,

        // 메트릭 조회 시간 범위.
        String range,

        // 메트릭 목록.
        List<FailureMetricResponse> metrics
) {
}
