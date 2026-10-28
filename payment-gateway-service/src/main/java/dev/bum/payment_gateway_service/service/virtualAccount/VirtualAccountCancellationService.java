package dev.bum.payment_gateway_service.service.virtualAccount;

import dev.bum.common.service.ticket.payment.dto.GatewayVirtualAccountCancelRequest;
import dev.bum.common.service.ticket.payment.dto.GatewayVirtualAccountCancelResponse;
import dev.bum.common.service.ticket.payment.enums.GatewayVirtualAccountStatus;
import dev.bum.payment_gateway_service.jpa.virtualAccount.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class VirtualAccountCancellationService {
    private final DummyVirtualAccountJpaRepository accounts;
    private final DummyVirtualAccountPaymentHistoryJpaRepository histories;
    private final EntityManager entityManager;

    @Transactional
    public GatewayVirtualAccountCancelResponse cancel(GatewayVirtualAccountCancelRequest request) {
        DummyVirtualAccount account = accounts.findByPaymentNoForUpdate(request.paymentNo())
                .orElseThrow(() -> new IllegalArgumentException("가상계좌 정보를 찾을 수 없습니다."));
        entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE);
        if (!Objects.equals(account.getAccountNumber(), request.accountNumber())
                || !Objects.equals(account.getBankName(), request.bankName())
                || request.amount() == null || account.getAmount().compareTo(request.amount()) != 0) {
            throw new IllegalArgumentException("가상계좌 취소 정보가 일치하지 않습니다.");
        }
        if (account.getStatus() != VirtualAccountPaymentStatus.CANCELLED) {
            // 포트폴리오 정책: 늦게 입금된 돈의 실제 반환은 구현하지 않는다. 최초 입금 정보와 이력은 보존한다.
            account.cancel("TICKET_RESERVATION_CANCELLED");
            histories.save(DummyVirtualAccountPaymentHistory.cancelled(account));
        }
        return new GatewayVirtualAccountCancelResponse(account.getPaymentNo(), account.getBankName(),
                account.getAccountNumber(), account.getAmount(), GatewayVirtualAccountStatus.CANCELLED);
    }
}
