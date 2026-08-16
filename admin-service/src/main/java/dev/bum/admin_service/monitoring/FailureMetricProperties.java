package dev.bum.admin_service.monitoring;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "app.monitoring.failure-metrics")
public class FailureMetricProperties {

    private final Map<String, Threshold> thresholds = new HashMap<>();

    public Map<String, Threshold> getThresholds() {
        return thresholds;
    }

    public Threshold thresholdOf(String key, double defaultWarningThreshold, double defaultCriticalThreshold) {
        Threshold threshold = thresholds.get(key);
        if (threshold == null) {
            return new Threshold(defaultWarningThreshold, defaultCriticalThreshold);
        }

        if (threshold.getWarningThreshold() == null) {
            threshold.setWarningThreshold(defaultWarningThreshold);
        }
        if (threshold.getCriticalThreshold() == null) {
            threshold.setCriticalThreshold(defaultCriticalThreshold);
        }
        return threshold;
    }


    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Threshold {
        private Double warningThreshold;
        private Double criticalThreshold;
    }
}
