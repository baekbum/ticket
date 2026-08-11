package dev.bum.admin_service.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PrometheusQueryClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${app.monitoring.prometheus-url:http://localhost:9090}")
    private String prometheusUrl;

    public Double querySingleValue(String promql) {
        JsonNode response = query(promql);

        if (response == null || !"success".equals(response.path("status").asText())) {
            return null;
        }

        JsonNode result = response.path("data").path("result");
        if (!result.isArray() || result.isEmpty()) {
            return null;
        }

        JsonNode value = result.get(0).path("value");
        if (!value.isArray() || value.size() < 2) {
            return null;
        }

        return parseDouble(value.get(1).asText());
    }

    public List<FailureMetricDetailResponse> queryDetails(String promql) {
        JsonNode response = query(promql);

        if (response == null || !"success".equals(response.path("status").asText())) {
            return List.of();
        }

        JsonNode result = response.path("data").path("result");
        if (!result.isArray() || result.isEmpty()) {
            return List.of();
        }

        List<FailureMetricDetailResponse> details = new ArrayList<>();
        for (JsonNode item : result) {
            Map<String, String> labels = labelsOf(item.path("metric"));
            JsonNode value = item.path("value");
            Double numericValue = value.isArray() && value.size() >= 2
                    ? parseDouble(value.get(1).asText())
                    : null;

            details.add(new FailureMetricDetailResponse(
                    serviceNameOf(labels),
                    labels.get("job"),
                    labels.get("instance"),
                    numericValue,
                    labels
            ));
        }
        return details;
    }

    private JsonNode query(String promql) {
        URI uri = UriComponentsBuilder.fromUriString(prometheusUrl)
                .path("/api/v1/query")
                .queryParam("query", promql)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();

        JsonNode response = restClientBuilder.build()
                .get()
                .uri(uri)
                .retrieve()
                .body(JsonNode.class);
        return response;
    }

    private Double parseDouble(String textValue) {
        try {
            return Double.parseDouble(textValue);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, String> labelsOf(JsonNode metric) {
        Map<String, String> labels = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = metric.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            labels.put(field.getKey(), field.getValue().asText());
        }
        return labels;
    }

    private String serviceNameOf(Map<String, String> labels) {
        String application = labels.get("application");
        if (application != null && !application.isBlank()) {
            return application;
        }

        String job = labels.get("job");
        if (job != null && !job.isBlank()) {
            return job;
        }

        return labels.getOrDefault("instance", "unknown");
    }
}
