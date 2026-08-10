package dev.bum.admin_service.monitoring;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(FailureMetricProperties.class)
public class FailureMetricService {

    private static final String DEFAULT_RANGE = "5m";

    private static final List<FailureMetricDefinition> DEFINITIONS = List.of(
            new FailureMetricDefinition(
                    "service_down_count",
                    "서비스 Down",
                    "Prometheus scrape 기준으로 내려간 서비스 수",
                    "count",
                    "sum(up{job=~\"local-(auth|user|ticket|queue|audit|admin)-service|ticket-services|admin-service\"} == bool 0) or vector(0)",
                    1,
                    1
            ),
            new FailureMetricDefinition(
                    "http_5xx_rate",
                    "HTTP 5xx 비율",
                    "전체 HTTP 요청 중 5xx 응답 비율",
                    "%",
                    "(sum(rate(http_server_requests_seconds_count{status=~\"5..\"}[{range}])) / sum(rate(http_server_requests_seconds_count[{range}])) * 100) or vector(0)",
                    1,
                    5
            ),
            new FailureMetricDefinition(
                    "http_p95_latency",
                    "HTTP p95 지연시간",
                    "HTTP 요청 처리 시간 p95",
                    "seconds",
                    "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[{range}])) by (le)) or vector(0)",
                    1,
                    3
            ),
            new FailureMetricDefinition(
                    "feign_timeout_count",
                    "Feign timeout",
                    "서비스 간 호출 timeout 추정 발생 수",
                    "count",
                    "sum(increase(http_client_requests_seconds_count{exception=~\".*Timeout.*|.*SocketTimeout.*|.*RetryableException.*\"}[{range}])) or vector(0)",
                    1,
                    5
            ),
            new FailureMetricDefinition(
                    "redis_command_error_count",
                    "Redis 명령 실패",
                    "Redis 명령 실행 중 예외 발생 수",
                    "count",
                    "sum(increase(redis_commands_duration_seconds_count{exception!=\"none\"}[{range}])) or vector(0)",
                    1,
                    3
            ),
            new FailureMetricDefinition(
                    "queue_token_complete_failure_count",
                    "Queue token 완료 실패",
                    "Queue token 회수/완료 처리 실패 수",
                    "count",
                    "sum(increase(queue_token_complete_failure_total[{range}])) or vector(0)",
                    1,
                    3
            ),
            new FailureMetricDefinition(
                    "kafka_dlq_publish_count",
                    "Kafka DLQ 적재",
                    "DLQ/DLT topic으로 이동한 메시지 수",
                    "count",
                    "sum(increase(kafka_dlt_publish_total[{range}])) or vector(0)",
                    1,
                    1
            )
    );

    private final PrometheusQueryClient prometheusQueryClient;
    private final FailureMetricProperties failureMetricProperties;

    public FailureMetricSummaryResponse failureMetrics(String range) {
        String resolvedRange = StringUtils.hasText(range) ? range : DEFAULT_RANGE;
        List<FailureMetricResponse> metrics = DEFINITIONS.stream()
                .map(definition -> definition.toResponse(
                        resolvedRange,
                        prometheusQueryClient.querySingleValue(definition.promql(resolvedRange)),
                        failureMetricProperties.thresholdOf(
                                definition.key(),
                                definition.defaultWarningThreshold(),
                                definition.defaultCriticalThreshold()
                        )
                ))
                .toList();

        return new FailureMetricSummaryResponse(OffsetDateTime.now(), resolvedRange, metrics);
    }
}
