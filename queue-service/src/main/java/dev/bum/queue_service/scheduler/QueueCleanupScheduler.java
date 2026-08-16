package dev.bum.queue_service.scheduler;

import dev.bum.queue_service.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QueueCleanupScheduler {

    private final QueueService queueService;

    @Scheduled(fixedDelayString = "${app.queue.cleanup-fixed-delay-ms:20000}")
    public void cleanupExpiredTokens() {
        queueService.cleanupExpiredTokens();
    }
}
