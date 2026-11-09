package dev.bum.payment_gateway_service.service.gateway.virtualAccount;

import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.payment_gateway_service.dto.bank.BankVirtualAccountDepositResponse;
import dev.bum.payment_gateway_service.dto.gateway.GatewayVirtualAccountPaymentDepositResponse;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountDepositRequest;
import dev.bum.payment_gateway_service.exception.*;
import dev.bum.payment_gateway_service.jpa.gateway.*;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GatewayVirtualAccountDepositCommands {

    // 은행의 PG 참조로 원거래를 찾고 PG 상태 확정 때만 잠근다.
    private final GatewayPaymentJpaRepository payments;
    // 발급 스냅샷 검증과 최초 입금 작업의 멱등 이력.
    private final GatewayPaymentHistoryJpaRepository histories;
    // 금융 성공과 구분한 가맹점 통보 Outbox.
    private final GatewayBankNotificationJpaRepository notifications;

    /**
     * 금융 실행 전에 요청 금액과 저장된 결제수단을 검증한다. 외부 HTTP 동안 락을 유지하지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void inspect(String pgPaymentId, GatewayVirtualAccountDepositRequest request) {

        GatewayPayment payment = payments.findById(pgPaymentId).orElseThrow(GatewayPaymentNotFoundException::new);

        if (payment.getPaymentMethod() != PaymentMethod.BANK_TRANSFER || request.getAmount() == null
                || payment.getAmount().compareTo(request.getAmount()) != 0) {
            throw new GatewayPaymentConflictException("입금 요청이 준비된 PG 원거래와 일치하지 않습니다.");
        }
    }

    /**
     * 최초 은행 입금과 연결을 검증하고 PG 상태·입금 이력·통보를 함께 커밋한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GatewayVirtualAccountPaymentDepositResponse confirm(
            String expectedId,
            BankVirtualAccountDepositResponse bank
    ) {

        GatewayPayment payment = payments.findForUpdate(expectedId).orElseThrow(GatewayPaymentNotFoundException::new);
        validate(payment, bank);
        Optional<GatewayPaymentHistory> deposited = histories.findByPaymentPgPaymentIdAndOperationTypeAndOperationKey(
                expectedId, GatewayPaymentOperation.DEPOSIT, "INITIAL");

        if (deposited.isPresent()) {
            if (!deposited.get().getDetails().get("depositedAt").equals(bank.depositedAt().toString())
                    || !deposited.get().getDetails().get("depositorName").equals(bank.depositorName())
                    || !deposited.get().getDetails().get("accountNumber").equals(bank.accountNumber())) {
                throw new GatewayPaymentConflictException("같은 PG 거래의 최초 입금 사실이 일치하지 않습니다.");
            }

            return GatewayVirtualAccountPaymentDepositResponse.from(payment, bank,
                    notifications.findByPaymentPgPaymentId(expectedId).orElseThrow().getStatus());
        }

        if (payment.getStatus() == GatewayPaymentStatus.READY) {
            payment.waitForDeposit();
            histories.saveAndFlush(GatewayPaymentHistory.accountIssued(payment, bank.accountNumber(), bank.bankName(),
                    VirtualAccountPaymentStatus.WAITING_DEPOSIT.name(), GatewayPaymentStatus.READY));
        }

        GatewayPaymentStatus before = payment.getStatus();
        payment.receiveDeposit(bank.depositedAt());
        histories.saveAndFlush(GatewayPaymentHistory.deposited(payment, bank, before));
        GatewayBankNotification event = notifications.saveAndFlush(GatewayBankNotification.pending(payment, bank));

        return GatewayVirtualAccountPaymentDepositResponse.from(payment, bank, event.getStatus());
    }

    /**
     * 응답 유실 복구에서도 다른 계좌·금액·은행·기한의 금융 결과를 수용하지 않는다.
     */
    private void validate(GatewayPayment payment, BankVirtualAccountDepositResponse bank) {

        if (bank == null || !payment.getPgPaymentId().equals(bank.pgPaymentId())
                || payment.getPaymentMethod() != PaymentMethod.BANK_TRANSFER
                || payment.getBankCompany() != bank.bankCompany() || bank.amount() == null
                || payment.getAmount().compareTo(bank.amount()) != 0
                || !payment.getExpiresAt().equals(bank.expiresAt())
                || !payment.getBankCompany().getBankName().equals(bank.bankName())
                || bank.accountNumber() == null || bank.accountNumber().isBlank() || bank.accountNumber().length() > 30
                || bank.depositorName() == null || bank.depositorName().isBlank() || bank.depositorName().length() > 50
                || bank.depositedAt() == null || bank.depositedAt().isAfter(payment.getExpiresAt())
                || (bank.status() != VirtualAccountPaymentStatus.DEPOSITED
                    && bank.status() != VirtualAccountPaymentStatus.CANCELLED)) {
            throw new FinancialProviderResponseException();
        }

        histories.findByPaymentPgPaymentIdAndOperationTypeAndOperationKey(payment.getPgPaymentId(),
                GatewayPaymentOperation.ISSUE_ACCOUNT, "INITIAL").ifPresent(history -> {
                    if (!bank.accountNumber().equals(history.getDetails().get("accountNumber"))) {
                        throw new FinancialProviderResponseException();
                    }
                });
    }
}
