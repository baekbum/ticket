package dev.bum.payment_gateway_service.service.gateway.virtualAccount;

import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.payment_gateway_service.dto.bank.BankExpirationEvent;
import dev.bum.payment_gateway_service.exception.*;
import dev.bum.payment_gateway_service.jpa.gateway.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class GatewayVirtualAccountExpirationCommands {

    // 은행이 연결한 PG 원거래만 잠가 상태 변경을 확정한다.
    private final GatewayPaymentJpaRepository payments;
    // 거래·EXPIRE·INITIAL의 최초 사실을 멱등 처리한다.
    private final GatewayPaymentHistoryJpaRepository histories;

    /**
     * 은행 만료를 PG 상태·이력과 함께 커밋한다. 입금 완료를 시간만으로 만료시키지 않는다.
     */
    public void confirm(BankExpirationEvent event) {

        event.validate();
        GatewayPayment payment = payments.findForUpdate(event.pgPaymentId())
                .orElseThrow(GatewayPaymentNotFoundException::new);

        if (payment.getPaymentMethod() != PaymentMethod.BANK_TRANSFER
                || payment.getBankCompany() != event.bankCompany()
                || payment.getAmount().compareTo(event.amount()) != 0
                || !payment.getExpiresAt().equals(event.expiresAt()) || payment.getPaidAt() != null) {
            throw new GatewayPaymentConflictException("은행 만료 사실이 PG 원거래와 일치하지 않습니다.");
        }

        Map<String, Object> details = Map.of("eventId", event.eventId(), "accountNumber", event.accountNumber(),
                "bankCompany", event.bankCompany().name(), "expiresAt", event.expiresAt().toString(),
                "expiredAt", event.expiredAt().toString());
        Optional<GatewayPaymentHistory> previous = histories.findByPaymentPgPaymentIdAndOperationTypeAndOperationKey(
                event.pgPaymentId(), GatewayPaymentOperation.EXPIRE, "INITIAL");

        if (previous.isPresent()) {
            if (!previous.get().getDetails().equals(details)) {
                throw new GatewayPaymentConflictException("같은 만료 이벤트의 최초 내용과 일치하지 않습니다.");
            }

            return;
        }

        histories.findByPaymentPgPaymentIdAndOperationTypeAndOperationKey(event.pgPaymentId(),
                GatewayPaymentOperation.ISSUE_ACCOUNT, "INITIAL").ifPresent(history -> {
                    if (!event.accountNumber().equals(history.getDetails().get("accountNumber"))) {
                        throw new GatewayPaymentConflictException("최초 발급 계좌와 은행 만료 계좌가 다릅니다.");
                    }
                });

        GatewayPaymentStatus before = payment.getStatus();
        payment.expireVirtualAccount();
        histories.saveAndFlush(GatewayPaymentHistory.virtualAccountExpired(payment, event, before));
    }
}
