package dev.bum.admin_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "app.kafka-dlq")
public class KafkaDlqProperties {

    private Map<String, String> mappings = new HashMap<>();

    public Map<String, String> getMappings() {
        return mappings;
    }

    public void setMappings(Map<String, String> mappings) {
        if (mappings != null) {
            this.mappings = mappings;
        }
    }

    public String targetTopicOf(String dltTopic) {
        return mappings.get(dltTopic);
    }
}
