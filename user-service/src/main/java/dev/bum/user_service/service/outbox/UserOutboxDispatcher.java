package dev.bum.user_service.service.outbox;

import dev.bum.user_service.jpa.outbox.UserOutboxEventJpaRepository;
import dev.bum.user_service.jpa.outbox.UserOutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserOutboxDispatcher {
    private final UserOutboxEventJpaRepository repository;
    private final UserOutboxPublisher publisher;

    @Value("${app.user-outbox.batch-size:100}")
    private int batchSize = 100;

    public void publishSafely(Long outboxId) {
        // 중단 요청이 있으면 인터럽트 표시를 유지한 채 다음 이벤트 발행을 건너뛴다.
        if (Thread.currentThread().isInterrupted()) {
            return;
        }

        try {
            publisher.publish(outboxId);
        } catch (RuntimeException e) {
            // 회원 변경은 이미 커밋됐다. 발행 상태 저장 실패도 요청 실패로 바꾸지 않고 재시도한다.
            log.warn("회원 Outbox 처리 실패: outboxId={}, errorType={}", outboxId, e.getClass().getSimpleName());
        }
    }

    public void retryPendingEvents() {
        repository.findReadyIds(UserOutboxStatus.PENDING, LocalDateTime.now(), PageRequest.of(0, batchSize))
                .forEach(this::publishSafely);
    }
}
