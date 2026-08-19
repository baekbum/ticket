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
        List<DummyVirtualAccount> expiredAccounts =
                dummyVirtualAccountJpaRepository.findTop100ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                        VirtualAccountPaymentStatus.WAITING_DEPOSIT,
                        now
                );

        for (DummyVirtualAccount virtualAccount : expiredAccounts) {
            expireWaitingAccount(virtualAccount, now);
        }
    }

    private void expireWaitingAccount(DummyVirtualAccount virtualAccount, LocalDateTime expiredAt) {
        try {
            virtualAccountExpiredEventProducer.sendExpired(virtualAccount, expiredAt).join();
            virtualAccount.expire();
            dummyVirtualAccountPaymentHistoryJpaRepository.save(
                    DummyVirtualAccountPaymentHistory.expired(virtualAccount)
            );
        } catch (RuntimeException e) {
            log.warn("가상계좌 만료 이벤트 발행 건너뜀: paymentNo={}", virtualAccount.getPaymentNo(), e);
        }
    }
}
