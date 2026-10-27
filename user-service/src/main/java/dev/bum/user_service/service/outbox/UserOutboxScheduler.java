package dev.bum.user_service.service.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.user-outbox.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class UserOutboxScheduler {
    private final UserOutboxDispatcher dispatcher;

    @Scheduled(fixedDelayString = "${app.user-outbox.fixed-delay-ms:5000}")
    public void retryPendingEvents() {
        dispatcher.retryPendingEvents();
    }
}
