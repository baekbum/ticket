package dev.bum.payment_gateway_service.service.financial.virtualAccount;

import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional(propagation = Propagation.NEVER)
@ConditionalOnProperty(name = "app.bank.expiration.enabled", havingValue = "true", matchIfMissing = true)
public class BankVirtualAccountExpirationScheduler {

    // 한 실행에서 더 오래된 오류 행이 뒤의 만료 대상을 가리지 않도록 ID로 순회한다.
    private final DummyVirtualAccountJpaRepository accounts;
    // 금융 상태와 이벤트는 건별 짧은 트랜잭션으로 확정한다.
    private final BankVirtualAccountExpirationService expirations;

    /**
     * 신규 PG 연결 계좌의 실제 미입금 만료를 은행 책임으로 처리한다.
     */
    @Scheduled(fixedDelayString = "${app.bank.expiration.fixed-delay-ms:30000}")
    public void expireWaitingAccounts() {

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        String afterId = "";

        while (!Thread.currentThread().isInterrupted()) {
            List<String> ids = accounts.findLinkedExpirationCandidates(now, afterId,
                    VirtualAccountPaymentStatus.WAITING_DEPOSIT, PageRequest.of(0, 100));

            if (ids.isEmpty()) {
                return;
            }

            for (String id : ids) {
                try {
                    expirations.expire(id, now);
                } catch (RuntimeException failure) {
                    log.warn("은행 계좌 만료 저장 실패: pgPaymentId={}, errorType={}", id,
                            failure.getClass().getSimpleName());
                }
            }

            afterId = ids.getLast();
        }
    }
}
