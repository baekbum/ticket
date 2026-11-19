package dev.bum.admin_service.monitoring;

import java.util.Map;

public record FailureMetricDetailResponse(
        // 이름.
        String name,

        // 모니터링 수집 작업 이름.
        String job,

        // 모니터링 대상 인스턴스.
        String instance,

        // 데이터 값.
        Double value,

        // 메트릭 분류 레이블.
        Map<String, String> labels
) {
}
