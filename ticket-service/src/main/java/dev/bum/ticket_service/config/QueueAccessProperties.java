package dev.bum.ticket_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "app.queue")
public class QueueAccessProperties {

    private boolean enabled;
    private String baseUrl = "http://localhost:8083/queue";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        if (StringUtils.hasText(baseUrl)) {
            this.baseUrl = baseUrl;
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public String baseUrl() {
        return baseUrl;
    }
}
