package dev.bum.payment_gateway_service.service.virtualAccount;

import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccount;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountPaymentHistory;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountPaymentHistoryJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentStatus;
import dev.bum.payment_gateway_service.jpa.outbox.VirtualAccountOutboxEvent;
import dev.bum.payment_gateway_service.jpa.outbox.VirtualAccountOutboxEventJpaRepository;
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

    // 모의 가상계좌 데이터를 조회하고 저장하는 레포지토리.
    private final DummyVirtualAccountJpaRepository dummyVirtualAccountJpaRepository;
    // 모의 가상계좌 결제 이력 데이터를 조회하고 저장하는 레포지토리.
    private final DummyVirtualAccountPaymentHistoryJpaRepository dummyVirtualAccountPaymentHistoryJpaRepository;
    // 가상계좌 Outbox 이벤트 데이터를 조회하고 저장하는 레포지토리.
    private final VirtualAccountOutboxEventJpaRepository virtualAccountOutboxEventJpaRepository;

    /**
     * 입금 대기 중 만료된 가상계좌를 처리한다.
     */
    @Scheduled(cron = "${app.virtual-account.expiration.cron:0 0 0 * * *}")
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

    /**
     * 만료 대상 가상계좌를 묶음 단위로 처리한다.
     */
    private void expireWaitingAccountBatch(List<DummyVirtualAccount> expiredAccounts, LocalDateTime expiredAt) {

        for (DummyVirtualAccount virtualAccount : expiredAccounts) {
            // 잠금을 획득한 계좌 중 입금 대기 상태이고 기한이 지난 경우에만 만료한다.
            if (virtualAccount.getStatus() != VirtualAccountPaymentStatus.WAITING_DEPOSIT
                    || virtualAccount.getExpiresAt().isAfter(expiredAt)) {
                continue;
            }
            virtualAccount.expire();

            dummyVirtualAccountPaymentHistoryJpaRepository.save(
                    DummyVirtualAccountPaymentHistory.expired(virtualAccount)
            );

            virtualAccountOutboxEventJpaRepository.save(
                    VirtualAccountOutboxEvent.virtualAccountExpired(virtualAccount, expiredAt)
            );

            log.info("가상계좌 만료 outbox 저장: paymentNo={}", virtualAccount.getPaymentNo());
        }
    }
}
