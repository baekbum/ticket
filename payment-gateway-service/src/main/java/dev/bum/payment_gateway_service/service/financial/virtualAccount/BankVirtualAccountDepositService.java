package dev.bum.payment_gateway_service.service.financial.virtualAccount;

import dev.bum.payment_gateway_service.dto.bank.BankVirtualAccountDepositResponse;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountDepositRequest;
import dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException;
import dev.bum.payment_gateway_service.jpa.virtualAccount.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BankVirtualAccountDepositService {

    // 은행은 계좌 행만 잠가 최초 입금과 중복 요청을 직렬화한다.
    private final DummyVirtualAccountJpaRepository accounts;
    // 입금과 은행 이력은 같은 금융 트랜잭션으로 저장한다.
    private final DummyVirtualAccountPaymentHistoryJpaRepository histories;

    /**
     * PG 연결 계좌의 입금을 한 번 반영한다. 가맹점 조회·Ticksy 호출·PG Outbox 저장은 하지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BankVirtualAccountDepositResponse deposit(GatewayVirtualAccountDepositRequest request) {

        DummyVirtualAccount account = accounts.findLinkedByAccountNumberForUpdate(request.getAccountNumber())
                .orElseThrow(() -> new IllegalArgumentException("PG 연결 가상계좌를 찾을 수 없습니다."));

        if (request.getAmount() == null || account.getAmount().compareTo(request.getAmount()) != 0
                || request.getDepositorName() == null || request.getDepositorName().isBlank()
                || request.getDepositorName().length() > 50) {
            throw new GatewayPaymentConflictException("입금 금액 또는 입금자 정보가 올바르지 않습니다.");
        }

        if (account.getDepositedAt() != null) {
            if (!account.getDepositorName().equals(request.getDepositorName())
                    || (request.getDepositedAt() != null && !account.getDepositedAt().equals(
                            request.getDepositedAt().truncatedTo(ChronoUnit.MICROS)))) {
                throw new GatewayPaymentConflictException("이미 처리된 최초 입금 정보와 요청이 일치하지 않습니다.");
            }

            return BankVirtualAccountDepositResponse.from(account);
        }

        LocalDateTime depositedAt = (request.getDepositedAt() == null ? LocalDateTime.now() : request.getDepositedAt())
                .truncatedTo(ChronoUnit.MICROS);

        if (account.getStatus() != VirtualAccountPaymentStatus.WAITING_DEPOSIT
                || depositedAt.isAfter(account.getExpiresAt())) {
            throw new GatewayPaymentConflictException("입금 처리할 수 없는 계좌 상태 또는 입금 기한입니다.");
        }

        account.deposit(request.getDepositorName(), depositedAt);
        histories.saveAndFlush(DummyVirtualAccountPaymentHistory.deposited(account));

        return BankVirtualAccountDepositResponse.from(account);
    }

    /**
     * 저장된 최초 입금으로 HTTP 응답 유실·PG 저장 실패를 복구한다.
     */
    @Transactional(readOnly = true)
    public Optional<BankVirtualAccountDepositResponse> find(String pgPaymentId) {

        return accounts.findByPgPaymentId(pgPaymentId).filter(account -> account.getDepositedAt() != null)
                .map(BankVirtualAccountDepositResponse::from);
    }

    /**
     * 가맹점의 취소 예매 응답에 따라 계좌를 정리한다. 최초 입금 사실을 보존하며 실제 반환은 하지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BankVirtualAccountDepositResponse rejectDeposit(String pgPaymentId) {

        DummyVirtualAccount account = accounts.findLinkedByPgPaymentIdForUpdate(pgPaymentId).orElseThrow();

        if (account.getDepositedAt() == null) {
            throw new GatewayPaymentConflictException("확인된 최초 입금이 필요합니다.");
        }

        if (account.getStatus() != VirtualAccountPaymentStatus.CANCELLED) {
            account.cancel("MERCHANT_RESERVATION_CANCELLED_NO_FUNDS_RETURNED");
            histories.saveAndFlush(DummyVirtualAccountPaymentHistory.cancelled(account));
        }

        return BankVirtualAccountDepositResponse.from(account);
    }
}
