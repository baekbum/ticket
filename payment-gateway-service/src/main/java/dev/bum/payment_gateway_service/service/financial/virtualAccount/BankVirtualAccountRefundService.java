package dev.bum.payment_gateway_service.service.financial.virtualAccount;

import dev.bum.common.service.paymentgateway.refund.*;
import dev.bum.payment_gateway_service.jpa.virtualAccount.*;
import dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Transactional
public class BankVirtualAccountRefundService {

    // 환불과 철회가 같은 은행 계좌를 잠가 금융 실행 순서를 결정한다.
    private final DummyVirtualAccountJpaRepository accounts;

    // 최초 금융 결과 또는 철회 tombstone을 보존한다.
    private final BankVirtualAccountRefundJpaRepository refunds;

    // 계좌 누적 환불액과 은행 이력을 함께 커밋한다.
    private final DummyVirtualAccountPaymentHistoryJpaRepository histories;

    // 잠금 대기 중 커밋된 금융 상태를 다시 읽는다.
    private final EntityManager entityManager;

    /**
     * 동일 요청의 최초 환불 결과를 재사용한다. 철회가 먼저 확정됐으면 금융 실행을 하지 않는다.
     */
    public BankRefundResult refund(BankRefundCommand request) {

        return execute(request, false);
    }

    /**
     * 미실행 요청을 영구 종료하고 지연 환불을 차단한다. 성공한 환불은 성공 결과를 반환한다.
     */
    public BankRefundResult withdraw(BankRefundCommand request) {

        return execute(request, true);
    }

    /**
     * 계좌 잠금 아래 결과 기록·누적 환불액·이력의 원자성을 보장한다.
     */
    private BankRefundResult execute(BankRefundCommand request, boolean withdraw) {

        request.validate();
        DummyVirtualAccount account = accounts.findLinkedByPgPaymentIdForUpdate(request.pgPaymentId())
                .orElseThrow(() -> new GatewayPaymentConflictException("환불 원계좌가 없습니다."));
        entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE);
        BankVirtualAccountRefund existing = refunds.findByPgPaymentIdAndRefundRequestId(
                request.pgPaymentId(), request.refundRequestId()).orElse(null);

        if (existing != null) {
            existing.validate(request);
            return existing.result();
        }

        BankVirtualAccountRefund row = BankVirtualAccountRefund.pending(request);

        if (!withdraw) {
            account.refundGateway(request.amount());
            String number = request.refundAccountNumber();
            String masked = "****" + number.substring(Math.max(0, number.length() - 4));
            histories.saveAndFlush(DummyVirtualAccountPaymentHistory.refunded(account,
                    request.refundBankCompany(), masked, request.refundAccountHolder(), request.amount()));
        }

        row.complete(withdraw ? "WITHDRAWN" : "SUCCEEDED",
                LocalDateTime.now(ZoneId.of("Asia/Seoul")).truncatedTo(ChronoUnit.MICROS));
        refunds.saveAndFlush(row);
        return row.result();
    }
}
