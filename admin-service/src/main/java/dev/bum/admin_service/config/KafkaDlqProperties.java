package dev.bum.admin_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "app.kafka-dlq")
public class KafkaDlqProperties {

    private Map<String, String> mappings = new HashMap<>();
    private List<Mapping> entries = List.of();

    public Map<String, String> getMappings() {
        Map<String, String> resolvedMappings = new HashMap<>(mappings);
        for (Mapping entry : entries) {
            if (StringUtils.hasText(entry.getDltTopic()) && StringUtils.hasText(entry.getTargetTopic())) {
                resolvedMappings.put(entry.getDltTopic(), entry.getTargetTopic());
            }
        }
        return resolvedMappings;
    }

    public void setMappings(Map<String, String> mappings) {
        if (mappings != null) {
            this.mappings = mappings;
        }
    }

    public List<Mapping> getEntries() {
        return entries;
    }

    public void setEntries(List<Mapping> entries) {
        if (entries != null) {
            this.entries = entries;
        }
    }

    public String targetTopicOf(String dltTopic) {
        return getMappings().get(dltTopic);
    }

    public static class Mapping {

        private String dltTopic;
        private String targetTopic;

        public String getDltTopic() {
            return dltTopic;
        }

        public void setDltTopic(String dltTopic) {
            this.dltTopic = dltTopic;
        }

        public String getTargetTopic() {
            return targetTopic;
        }

        public void setTargetTopic(String targetTopic) {
            this.targetTopic = targetTopic;
        }
    }
}
