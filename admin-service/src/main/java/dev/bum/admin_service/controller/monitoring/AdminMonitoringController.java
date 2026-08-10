package dev.bum.admin_service.controller.monitoring;

import dev.bum.admin_service.monitoring.FailureMetricService;
import dev.bum.admin_service.monitoring.FailureMetricSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/manage/monitoring")
public class AdminMonitoringController {

    private final FailureMetricService failureMetricService;

    @GetMapping("/failure-metrics")
    public ResponseEntity<FailureMetricSummaryResponse> failureMetrics(
            @RequestParam(value = "range", required = false) String range
    ) {
        return ResponseEntity.ok(failureMetricService.failureMetrics(range));
    }
}
