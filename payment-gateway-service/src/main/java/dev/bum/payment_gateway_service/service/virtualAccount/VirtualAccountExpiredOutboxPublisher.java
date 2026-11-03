package dev.bum.payment_gateway_service.service.virtualAccount;

import dev.bum.payment_gateway_service.jpa.outbox.OutboxEventStatus;
import dev.bum.payment_gateway_service.jpa.outbox.OutboxEventType;
import dev.bum.payment_gateway_service.jpa.outbox.VirtualAccountOutboxEvent;
import dev.bum.payment_gateway_service.jpa.outbox.VirtualAccountOutboxEventJpaRepository;
import dev.bum.payment_gateway_service.kafka.virtualAccount.VirtualAccountExpiredEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.virtual-account.expired-outbox-publisher.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class VirtualAccountExpiredOutboxPublisher {

    // 가상계좌 Outbox 이벤트 데이터를 조회하고 저장하는 레포지토리.
    private final VirtualAccountOutboxEventJpaRepository virtualAccountOutboxEventJpaRepository;
    // 가상계좌 만료 이벤트 이벤트 발행을 담당하는 도구.
    private final VirtualAccountExpiredEventProducer virtualAccountExpiredEventProducer;

    /**
     * 발행 대기 중인 이벤트를 발행한다.
     */
    @Scheduled(fixedDelayString = "${app.virtual-account.expired-outbox-publisher.fixed-delay-ms:30000}")
    @Transactional
    public void publishPendingEvents() {

        List<VirtualAccountOutboxEvent> pendingEvents =
                virtualAccountOutboxEventJpaRepository.findTop100ByEventTypeAndStatusOrderByOutboxIdAsc(
                        OutboxEventType.VIRTUAL_ACCOUNT_EXPIRED,
                        OutboxEventStatus.PENDING
                );

        if (pendingEvents.isEmpty()) {
            return;
        }

        List<CompletableFuture<PublishResult>> publishFutures = pendingEvents.stream()
                .map(this::publish)
                .toList();

        CompletableFuture.allOf(publishFutures.toArray(CompletableFuture[]::new)).join();
        publishFutures.stream()
                .map(CompletableFuture::join)
                .forEach(this::applyPublishResult);
    }

    /**
     * 가상계좌 만료 Outbox 이벤트 처리에서 이벤트를 발행한다.
     */
    private CompletableFuture<PublishResult> publish(VirtualAccountOutboxEvent outboxEvent) {

        return virtualAccountExpiredEventProducer.sendExpired(outboxEvent.toVirtualAccountExpiredEvent())
                .thenApply(result -> PublishResult.success(outboxEvent))
                .exceptionally(throwable -> PublishResult.failure(outboxEvent, throwable));
    }

    /**
     * 이벤트 발행 성공 또는 실패 결과를 저장한다.
     */
    private void applyPublishResult(PublishResult result) {

        if (result.success()) {
            result.outboxEvent().publish(LocalDateTime.now());
            return;
        }

        result.outboxEvent().failPublish(resolveErrorMessage(result.throwable()));
        log.warn("가상계좌 만료 outbox 발행 실패: outboxId={}, paymentNo={}",
                result.outboxEvent().getOutboxId(),
                result.outboxEvent().getPaymentNo(),
                result.throwable());
    }

    /**
     * 실패 원인에서 기록할 오류 메시지를 추출한다.
     */
    private String resolveErrorMessage(Throwable throwable) {

        Throwable resolvedThrowable = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
        if (resolvedThrowable == null || resolvedThrowable.getMessage() == null) {
            return "Kafka 발행 실패";
        }
        return resolvedThrowable.getMessage();
    }

    private record PublishResult(
            // 발행 대기 이벤트.
            VirtualAccountOutboxEvent outboxEvent,

            // 처리 성공 여부.
            boolean success,

            // 처리 중 발생한 예외.
            Throwable throwable
    ) {

        // 성공한 처리 결과를 구성한다.
        private static PublishResult success(VirtualAccountOutboxEvent outboxEvent) {

            return new PublishResult(outboxEvent, true, null);
        }

        // 실패한 처리 결과를 구성한다.
        private static PublishResult failure(VirtualAccountOutboxEvent outboxEvent, Throwable throwable) {

            return new PublishResult(outboxEvent, false, throwable);
        }
    }
}
