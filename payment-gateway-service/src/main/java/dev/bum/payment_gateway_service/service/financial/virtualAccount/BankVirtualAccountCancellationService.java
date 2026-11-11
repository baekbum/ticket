package dev.bum.payment_gateway_service.service.financial.virtualAccount;

import dev.bum.payment_gateway_service.dto.bank.*;
import dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException;
import dev.bum.payment_gateway_service.jpa.virtualAccount.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BankVirtualAccountCancellationService {

    // 입금과 같은 계좌 행을 잠가 어느 금융 처리가 먼저 커밋됐는지 결정한다.
    private final DummyVirtualAccountJpaRepository accounts;
    // 계좌 취소와 은행 취소 이력을 함께 저장한다.
    private final DummyVirtualAccountPaymentHistoryJpaRepository histories;
    // 은행의 최초 취소 성공·거절 결과를 재시도에도 그대로 돌려준다.
    private final BankVirtualAccountCancellationJpaRepository receipts;
    // 최초 입력과 업무 결과의 비밀값 없는 JSON 스냅샷 변환.
    private final ObjectMapper json;

    /**
     * HTTP 응답 유실이나 PG 저장 실패 후 은행에 이미 커밋된 취소만 조회한다.
     */
    @Transactional(readOnly = true)
    public Optional<BankVirtualAccountCancelResponse> find(String pgPaymentId) {

        return accounts.findByPgPaymentId(pgPaymentId)
                .filter(account -> account.getStatus() == VirtualAccountPaymentStatus.CANCELLED)
                .map(BankVirtualAccountCancelResponse::from);
    }

    /**
     * 은행이 취소 가능 여부를 판단하고 상태·이력·멱등 결과를 같은 트랜잭션에 저장한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BankVirtualAccountCancellationResult cancelCommand(BankVirtualAccountCancelRequest request) {

        DummyVirtualAccount account = accounts.findLinkedByPgPaymentIdForUpdate(request.pgPaymentId()).orElse(null);
        Optional<BankVirtualAccountCancellation> previous = receipts.findById(request.pgPaymentId());

        if (previous.isPresent()) {
            BankVirtualAccountCancelRequest original = json.convertValue(previous.get().getRequestPayload(),
                    BankVirtualAccountCancelRequest.class);

            if (!original.equals(request)) {
                throw new GatewayPaymentConflictException("같은 취소 키의 최초 요청 내용과 일치하지 않습니다.");
            }

            return json.convertValue(previous.get().getResultPayload(), BankVirtualAccountCancellationResult.class);
        }

        if (account != null && (account.getBankCompany() != request.bankCompany()
                || !account.getAccountNumber().equals(request.accountNumber()) || account.getAmount().compareTo(request.amount()) != 0)) {
            throw new GatewayPaymentConflictException("취소 요청이 은행 원계좌와 일치하지 않습니다.");
        }

        String outcome;
        String reason;

        if (account == null) {
            outcome = "REJECTED";
            reason = "ACCOUNT_NOT_FOUND";
        } else if (account.getStatus() == VirtualAccountPaymentStatus.CANCELLED) {
            outcome = "CANCELLED";
            reason = "ALREADY_CANCELLED";
        } else if (account.getDepositedAt() != null) {
            outcome = "REJECTED";
            reason = "ALREADY_DEPOSITED";
        } else if (account.getStatus() != VirtualAccountPaymentStatus.WAITING_DEPOSIT
                && account.getStatus() != VirtualAccountPaymentStatus.EXPIRED) {
            outcome = "REJECTED";
            reason = "ACCOUNT_NOT_CANCELLABLE";
        } else {
            account.cancel(BankVirtualAccountCancelResponse.REQUESTED_REASON);
            histories.saveAndFlush(DummyVirtualAccountPaymentHistory.cancelledByGateway(account));
            outcome = "CANCELLED";
            reason = BankVirtualAccountCancelResponse.REQUESTED_REASON;
        }

        BankVirtualAccountCancellationResult result = new BankVirtualAccountCancellationResult(
                request.pgPaymentId() + ":CANCEL:INITIAL", request, outcome, reason,
                account == null ? null : BankVirtualAccountCancelResponse.from(account));
        receipts.saveAndFlush(BankVirtualAccountCancellation.completed(request.pgPaymentId(),
                json.convertValue(request, new TypeReference<>() { }), json.convertValue(result, new TypeReference<>() { })));

        return result;
    }

}
