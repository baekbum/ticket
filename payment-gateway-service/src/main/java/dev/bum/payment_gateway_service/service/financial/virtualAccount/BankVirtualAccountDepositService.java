package dev.bum.payment_gateway_service.service.financial.virtualAccount;

import dev.bum.payment_gateway_service.dto.bank.BankVirtualAccountDepositResponse;
import dev.bum.payment_gateway_service.dto.bank.BankDepositEvent;
import dev.bum.payment_gateway_service.jpa.outbox.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountDepositRequest;
import dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException;
import dev.bum.payment_gateway_service.jpa.virtualAccount.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class BankVirtualAccountDepositService {

    // 은행은 계좌 행만 잠가 최초 입금과 중복 요청을 직렬화한다.
    private final DummyVirtualAccountJpaRepository accounts;
    // 입금과 은행 이력은 같은 금융 트랜잭션으로 저장한다.
    private final DummyVirtualAccountPaymentHistoryJpaRepository histories;
    // 은행 입금과 PG 통보 의도를 같은 은행 트랜잭션으로 확정한다.
    private final BankTransferOutboxJpaRepository notifications;
    // 불변 입금 DTO를 JSON 스냅샷으로 저장한다.
    private final ObjectMapper json;

    /**
     * PG 연결 계좌의 입금·은행 이력·은행→PG 통보 Outbox를 같은 트랜잭션으로 처리한다.
     * 호출자의 트랜잭션이 있으면 참여하고, 없으면 새로 시작한다.
     */
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

            enqueue(account);

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
        enqueue(account);

        return BankVirtualAccountDepositResponse.from(account);
    }

    /**
     * 전환 전 은행 입금 자료도 최초 금융 사실로 통보 의도를 복구한다. PG 상태는 직접 조회하지 않는다.
     */
    public void enqueueExistingDeposit(String pgPaymentId) {

        accounts.findLinkedByPgPaymentIdForUpdate(pgPaymentId).filter(account -> account.getDepositedAt() != null)
                .ifPresent(this::enqueue);
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

    /**
     * 최초 입금의 금융 사실을 고정한다. 취소 후 재시도도 입금 이벤트 내용은 바뀌지 않는다.
     */
    private void enqueue(DummyVirtualAccount account) {

        // 입금 사실을 PG로 보내기 위한 OUTBOX에 이미 해당 건이 존재하면, 넣지 않는다.
        if (notifications.findByFlowAndPgPaymentId(BankTransferOutbox.Flow.BANK_DEPOSIT, account.getPgPaymentId()).isPresent()) {
            return;
        }

        BankVirtualAccountDepositResponse bank = new BankVirtualAccountDepositResponse(account.getPgPaymentId(),
                account.getBankCompany(), account.getBankName(), account.getAccountNumber(), account.getAmount(),
                account.getExpiresAt(), account.getDepositorName(), account.getDepositedAt(),
                VirtualAccountPaymentStatus.DEPOSITED);
        BankDepositEvent event = BankDepositEvent.from(bank);
        notifications.saveAndFlush(BankTransferOutbox.pending(BankTransferOutbox.Flow.BANK_DEPOSIT, event.eventId(),
                account.getPgPaymentId(), json.convertValue(event, new TypeReference<>() { })));
    }
}
