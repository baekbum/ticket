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

    private final VirtualAccountOutboxEventJpaRepository virtualAccountOutboxEventJpaRepository;
    private final VirtualAccountExpiredEventProducer virtualAccountExpiredEventProducer;

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

    private CompletableFuture<PublishResult> publish(VirtualAccountOutboxEvent outboxEvent) {
        return virtualAccountExpiredEventProducer.sendExpired(outboxEvent.toVirtualAccountExpiredEvent())
                .thenApply(result -> PublishResult.success(outboxEvent))
                .exceptionally(throwable -> PublishResult.failure(outboxEvent, throwable));
    }

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
            VirtualAccountOutboxEvent outboxEvent,
            boolean success,
            Throwable throwable
    ) {

        private static PublishResult success(VirtualAccountOutboxEvent outboxEvent) {
            return new PublishResult(outboxEvent, true, null);
        }

        private static PublishResult failure(VirtualAccountOutboxEvent outboxEvent, Throwable throwable) {
            return new PublishResult(outboxEvent, false, throwable);
        }
    }
}
