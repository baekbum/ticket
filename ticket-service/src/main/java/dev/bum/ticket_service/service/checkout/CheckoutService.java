package dev.bum.ticket_service.service.checkout;

import dev.bum.common.service.ticket.checkout.dto.*;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.ticket_service.service.checkout.payment.VirtualAccountIssuanceCheckoutService;
import dev.bum.ticket_service.service.checkout.payment.CardCheckoutService;
import dev.bum.ticket_service.service.checkout.payment.CardToBankCheckoutService;
import dev.bum.ticket_service.service.checkout.payment.BankToCardCheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class CheckoutService {

    // 예매·좌석·Payment를 하나의 짧은 로컬 트랜잭션으로 저장한다.
    private final CheckoutTransactionService commands;

    // 커밋한 발급 의도로 PG prepare·발급을 재시도한다.
    private final VirtualAccountIssuanceCheckoutService accounts;

    // 카드도 로컬 저장 커밋 후 PG 거래를 준비한다.
    private final CardCheckoutService cardCheckout;

    private final CardToBankCheckoutService cardToBank;

    private final BankToCardCheckoutService bankToCard;

    /**
     * 로컬 예매를 먼저 커밋하고 신규 PG 거래를 준비·발급한다.
     */
    public PaymentResponse confirm(String userId, String activeToken, CheckoutConfirmRequest request) {

        CheckoutTransactionService.MethodChangeCandidate candidate = commands.findMethodChangeCandidate(userId, request);

        if (candidate != null && candidate.isCardToBank()) {
            return cardToBank.change(userId, activeToken, request);
        }

        if (candidate != null && candidate.isBankToCard()) {
            return bankToCard.change(userId, activeToken, request);
        }

        return completePayment(userId, commands.confirm(userId, activeToken, request));
    }

    private PaymentResponse completePayment(String userId, PaymentResponse payment) {

        if (payment.getMethod() == PaymentMethod.BANK_TRANSFER) {
            return accounts.issue(payment.getPaymentNo());
        }

        return cardCheckout.prepare(userId, payment.getPaymentNo());
    }

    /**
     * 기존 checkout 준비·소유권 검증을 로컬 트랜잭션으로 실행한다.
     */
    public CheckoutPrepareResponse prepare(String userId, String activeToken, CheckoutPrepareRequest request) {

        return commands.prepare(userId, activeToken, request);
    }
}
