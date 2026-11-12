package dev.bum.payment_gateway_service.service.financial.virtualAccount;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.payment_gateway_service.dto.bank.BankExpirationEvent;
import dev.bum.payment_gateway_service.jpa.outbox.*;
import dev.bum.payment_gateway_service.jpa.virtualAccount.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class BankVirtualAccountExpirationService {

    // 입금과 같은 계좌 행을 잠가 금융 결과를 직렬화한다.
    private final DummyVirtualAccountJpaRepository accounts;
    // 은행 계좌 만료 이력을 상태·통보 의도와 함께 저장한다.
    private final DummyVirtualAccountPaymentHistoryJpaRepository histories;
    // 은행→PG 만료 이벤트도 기존 전달 엔진을 사용한다.
    private final BankTransferOutboxJpaRepository notifications;
    // 비밀값 없는 최초 이벤트 스냅샷 변환.
    private final ObjectMapper json;
    // 잠금을 기다리는 동안 반영된 입금을 최신 상태로 읽는다.
    private final EntityManager entityManager;

    /**
     * 실제 미입금 계좌만 만료한다. 계좌·이력·Outbox는 하나의 은행 트랜잭션으로 커밋한다.
     */
    public boolean expire(String pgPaymentId, LocalDateTime now) {

        DummyVirtualAccount account = accounts.findLinkedByPgPaymentIdForUpdate(pgPaymentId).orElse(null);

        if (account == null) {
            return false;
        }

        entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE);

        if (account.getStatus() != VirtualAccountPaymentStatus.WAITING_DEPOSIT
                || account.getDepositedAt() != null || !now.isAfter(account.getExpiresAt())) {
            return false;
        }

        BankExpirationEvent event = new BankExpirationEvent(pgPaymentId + ":EXPIRE:INITIAL", pgPaymentId,
                account.getAccountNumber(), account.getBankCompany(), account.getAmount(), account.getExpiresAt(), now);
        event.validate();
        account.expire();
        histories.saveAndFlush(DummyVirtualAccountPaymentHistory.expired(account));
        notifications.saveAndFlush(BankTransferOutbox.pending(BankTransferOutbox.Flow.BANK_EXPIRATION,
                event.eventId(), pgPaymentId, json.convertValue(event, new TypeReference<>() { })));

        return true;
    }
}
