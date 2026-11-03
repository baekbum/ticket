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

    // 가상계좌 입금 Outbox 이벤트 데이터를 조회하고 저장하는 레포지토리.
    private final VirtualAccountDepositOutboxJpaRepository repository;
    // 가상계좌 입금 Outbox 이벤트 발행 대기 작업을 순회하고 실행하는 도구.
    private final VirtualAccountDepositOutboxDispatcher dispatcher;

    /**
     * 가상계좌 입금 Outbox 이벤트 처리에서 발행 대기 이벤트로 저장한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(DummyVirtualAccount account) {

        // 호출자가 계좌를 잠근 입금 트랜잭션 안에서 한 번만 생성한다.
        VirtualAccountDepositOutbox outbox = repository.findByPaymentNo(account.getPaymentNo())
                .orElseGet(() -> repository.save(VirtualAccountDepositOutbox.pending(account)));

        Long outboxId = outbox.getOutboxId();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            /**
             * 트랜잭션 커밋이 완료된 뒤 등록한 후속 작업을 실행한다.
             */
            @Override
            public void afterCommit() {

                // 입금과 Outbox가 커밋된 후 전달한다. 실패·서버 종료는 스케줄러가 복구한다.
                dispatcher.deliverSafely(outboxId);
            }
        });
    }
}
