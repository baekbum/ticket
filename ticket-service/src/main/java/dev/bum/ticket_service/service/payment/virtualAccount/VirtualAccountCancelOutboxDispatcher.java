package dev.bum.ticket_service.service.payment.virtualAccount;

import dev.bum.ticket_service.jpa.payment.virtualAccount.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class VirtualAccountCancelOutboxDispatcher {

    // 가상계좌 취소 Outbox 이벤트 데이터를 조회하고 저장하는 레포지토리.
    private final VirtualAccountCancelOutboxJpaRepository repository;
    // 가상계좌 취소 Outbox 이벤트 이벤트 발행을 담당하는 도구.
    private final VirtualAccountCancelOutboxPublisher publisher;
    // 한 번에 처리할 발행 대기 이벤트 수.
    @Value("${app.virtual-account.cancel-outbox.batch-size:100}")
    private int batchSize = 100;

    /**
     * 가상계좌 취소 Outbox 이벤트 처리에서 발행 대기 이벤트를 재시도한다.
     */
    @Scheduled(fixedDelayString = "${app.virtual-account.cancel-outbox.fixed-delay-ms:30000}")
    public void retryPendingEvents() {

        for (Long id : repository.findReadyIds(VirtualAccountCancelOutboxStatus.PENDING,
                LocalDateTime.now(), PageRequest.of(0, batchSize))) {
            if (Thread.currentThread().isInterrupted()) return;
            try {
                publisher.deliver(id);
            } catch (RuntimeException e) {
                // 완료 기록 커밋 실패도 PENDING 작업을 보존하여 같은 요청으로 복구한다.
                log.warn("가상계좌 취소 Outbox 처리 실패: outboxId={}, errorType={}", id, e.getClass().getSimpleName());
            }
        }
    }
}
