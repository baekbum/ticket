package dev.bum.payment_gateway_service.service.virtualAccount;

import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccount;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountPaymentHistory;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountPaymentHistoryJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentStatus;
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

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.virtual-account.expiration.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class VirtualAccountExpirationScheduler {

    private final DummyVirtualAccountJpaRepository dummyVirtualAccountJpaRepository;
    private final DummyVirtualAccountPaymentHistoryJpaRepository dummyVirtualAccountPaymentHistoryJpaRepository;
    private final VirtualAccountExpiredEventProducer virtualAccountExpiredEventProducer;

    @Scheduled(cron = "${app.virtual-account.expiration.cron:0 5 0 * * *}")
    @Transactional
    public void expireWaitingAccounts() {
        LocalDateTime now = LocalDateTime.now();

        while (true) {
            List<DummyVirtualAccount> expiredAccounts =
                    dummyVirtualAccountJpaRepository.findTop100ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                            VirtualAccountPaymentStatus.WAITING_DEPOSIT,
                            now
                    );
            if (expiredAccounts.isEmpty()) {
                return;
            }

            expireWaitingAccountBatch(expiredAccounts, now);
        }
    }

    private void expireWaitingAccountBatch(List<DummyVirtualAccount> expiredAccounts, LocalDateTime expiredAt) {
        List<CompletableFuture<Void>> publishFutures = expiredAccounts.stream()
                .map(virtualAccount -> expireAndPublish(virtualAccount, expiredAt))
                .toList();

        CompletableFuture.allOf(publishFutures.toArray(CompletableFuture[]::new)).join();
    }

    private CompletableFuture<Void> expireAndPublish(DummyVirtualAccount virtualAccount, LocalDateTime expiredAt) {
        virtualAccount.expire();
        dummyVirtualAccountPaymentHistoryJpaRepository.save(
                DummyVirtualAccountPaymentHistory.expired(virtualAccount)
        );

        return virtualAccountExpiredEventProducer.sendExpired(virtualAccount, expiredAt)
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        log.warn("가상계좌 만료 이벤트 발행 실패: paymentNo={}", virtualAccount.getPaymentNo(), throwable);
                    }
                    return null;
                });
    }
}
