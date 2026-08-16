package dev.bum.queue_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.queue")
public class QueueProperties {

    private int admissionSize = 100;
    private Duration activeTokenTtl = Duration.ofMinutes(20);
    private Duration waitingTokenTtl = Duration.ofMinutes(1);
    private long cleanupScanCount = 1_000L;

    public int getAdmissionSize() {
        return admissionSize;
    }

    public void setAdmissionSize(int admissionSize) {
        if (admissionSize > 0) {
            this.admissionSize = admissionSize;
        }
    }

    public Duration getActiveTokenTtl() {
        return activeTokenTtl;
    }

    public void setActiveTokenTtl(Duration activeTokenTtl) {
        if (activeTokenTtl != null) {
            this.activeTokenTtl = activeTokenTtl;
        }
    }

    public void setTokenTtl(Duration tokenTtl) {
        if (tokenTtl != null) {
            this.activeTokenTtl = tokenTtl;
        }
    }

    public Duration getWaitingTokenTtl() {
        return waitingTokenTtl;
    }

    public void setWaitingTokenTtl(Duration waitingTokenTtl) {
        if (waitingTokenTtl != null && !waitingTokenTtl.isNegative() && !waitingTokenTtl.isZero()) {
            this.waitingTokenTtl = waitingTokenTtl;
        }
    }

    public long getCleanupScanCount() {
        return cleanupScanCount;
    }

    public void setCleanupScanCount(long cleanupScanCount) {
        if (cleanupScanCount > 0) {
            this.cleanupScanCount = cleanupScanCount;
        }
    }

}
