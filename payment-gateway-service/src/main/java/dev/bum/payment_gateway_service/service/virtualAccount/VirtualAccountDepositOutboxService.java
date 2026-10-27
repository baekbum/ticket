package dev.bum.payment_gateway_service.service.virtualAccount;

import dev.bum.payment_gateway_service.jpa.outbox.VirtualAccountDepositOutbox;
import dev.bum.payment_gateway_service.jpa.outbox.VirtualAccountDepositOutboxJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class VirtualAccountDepositOutboxService {
    private final VirtualAccountDepositOutboxJpaRepository repository;
    private final VirtualAccountDepositOutboxDispatcher dispatcher;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(DummyVirtualAccount account) {
        // 호출자가 계좌를 잠근 입금 트랜잭션 안에서 한 번만 생성한다.
        VirtualAccountDepositOutbox outbox = repository.findByPaymentNo(account.getPaymentNo())
                .orElseGet(() -> repository.save(VirtualAccountDepositOutbox.pending(account)));

        Long outboxId = outbox.getOutboxId();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 입금과 Outbox가 커밋된 후 전달한다. 실패·서버 종료는 스케줄러가 복구한다.
                dispatcher.deliverSafely(outboxId);
            }
        });
    }
}
