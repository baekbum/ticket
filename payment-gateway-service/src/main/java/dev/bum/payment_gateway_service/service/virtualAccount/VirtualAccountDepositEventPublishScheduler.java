package dev.bum.payment_gateway_service.service.virtualAccount;

import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccount;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountPaymentHistory;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountPaymentHistoryJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentStatus;
import dev.bum.payment_gateway_service.kafka.virtualAccount.VirtualAccountDepositEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.virtual-account.deposit-complete-publisher.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class VirtualAccountDepositEventPublishScheduler {

    private final DummyVirtualAccountJpaRepository dummyVirtualAccountJpaRepository;
    private final DummyVirtualAccountPaymentHistoryJpaRepository dummyVirtualAccountPaymentHistoryJpaRepository;
    private final VirtualAccountDepositEventProducer virtualAccountDepositEventProducer;

    @Scheduled(fixedDelayString = "${app.virtual-account.deposit-complete-publisher.fixed-delay-ms:30000}")
    @Transactional
    public void publishDepositCompletedEvents() {
        List<DummyVirtualAccount> depositedAccounts =
                dummyVirtualAccountJpaRepository.findTop100ByStatusOrderByDepositedAtAsc(VirtualAccountPaymentStatus.DEPOSITED);

        for (DummyVirtualAccount virtualAccount : depositedAccounts) {
            publishDepositCompletedEvent(virtualAccount);
        }
    }

    private void publishDepositCompletedEvent(DummyVirtualAccount virtualAccount) {
        try {
            virtualAccountDepositEventProducer.sendDepositCompleted(virtualAccount).join();
            virtualAccount.publishDepositEvent();
            dummyVirtualAccountPaymentHistoryJpaRepository.save(
                    DummyVirtualAccountPaymentHistory.depositEventPublished(virtualAccount)
            );
        } catch (RuntimeException e) {
            log.warn("가상계좌 입금 완료 이벤트 발행 건너뜀: paymentNo={}", virtualAccount.getPaymentNo(), e);
        }
    }
}
