package dev.bum.queue_service.scheduler;

import dev.bum.queue_service.service.reactive.ReactiveQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.queue.cleanup-mode", havingValue = "reactive")
public class ReactiveQueueCleanupScheduler {
    private final ReactiveQueueService queueService;

    @Scheduled(fixedDelayString = "${app.queue.cleanup-fixed-delay-ms:20000}")
    public Mono<Void> cleanupExpiredTokens() {
        // Spring이 매 실행마다 구독한다. 작업 완료 후 fixedDelay를 계산한다.
        return Mono.defer(queueService::cleanupExpiredTokens);
    }
}
