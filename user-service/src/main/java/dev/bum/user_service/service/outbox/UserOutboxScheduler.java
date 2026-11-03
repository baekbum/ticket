package dev.bum.user_service.service.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.user-outbox.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class UserOutboxScheduler {

    // 사용자 Outbox 이벤트 발행 대기 작업을 순회하고 실행하는 도구.
    private final UserOutboxDispatcher dispatcher;

    /**
     * 사용자 Outbox 이벤트 처리에서 발행 대기 이벤트를 재시도한다.
     */
    @Scheduled(fixedDelayString = "${app.user-outbox.fixed-delay-ms:5000}")
    public void retryPendingEvents() {

        dispatcher.retryPendingEvents();
    }
}
