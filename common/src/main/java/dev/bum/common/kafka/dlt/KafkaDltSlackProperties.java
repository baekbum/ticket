package dev.bum.common.kafka.dlt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka-dlt.slack")
public class KafkaDltSlackProperties {

    private boolean enabled = true;
    private String webhookUrl;
    private int payloadPreviewLength = 500;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public int getPayloadPreviewLength() {
        return payloadPreviewLength;
    }

    public void setPayloadPreviewLength(int payloadPreviewLength) {
        this.payloadPreviewLength = payloadPreviewLength;
    }
}
