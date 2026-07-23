package dev.bum.queue_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.queue")
public class QueueProperties {

    private int admissionSize = 100;
    private Duration tokenTtl = Duration.ofMinutes(10);

    public int getAdmissionSize() {
        return admissionSize;
    }

    public void setAdmissionSize(int admissionSize) {
        if (admissionSize > 0) {
            this.admissionSize = admissionSize;
        }
    }

    public Duration getTokenTtl() {
        return tokenTtl;
    }

    public void setTokenTtl(Duration tokenTtl) {
        if (tokenTtl != null) {
            this.tokenTtl = tokenTtl;
        }
    }

    public int admissionSize() {
        return admissionSize;
    }

    public Duration tokenTtl() {
        return tokenTtl;
    }
}
