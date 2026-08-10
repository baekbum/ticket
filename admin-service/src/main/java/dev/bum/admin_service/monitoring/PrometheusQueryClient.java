package dev.bum.admin_service.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class PrometheusQueryClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${app.monitoring.prometheus-url:http://localhost:9090}")
    private String prometheusUrl;

    public Double querySingleValue(String promql) {
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

        String textValue = value.get(1).asText();
        try {
            return Double.parseDouble(textValue);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
