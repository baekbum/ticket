package dev.bum.ticket_service.service.checkout;

import dev.bum.common.service.ticket.checkout.dto.*;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayCardClient;
import dev.bum.ticket_service.service.checkout.payment.GatewayVirtualAccountCheckoutService;
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
    private final GatewayVirtualAccountCheckoutService accounts;
    // 기존 카드 승인 이력 확인은 DB 잠금 밖에서 수행한다.
    private final PaymentGatewayCardClient cards;

    /**
     * 로컬 예매를 먼저 커밋하고 신규 PG 거래를 준비·발급한다.
     */
    public PaymentResponse confirm(String userId, String activeToken, CheckoutConfirmRequest request) {

        String cardPaymentNo = commands.findCardSwitchPaymentNo(userId, request);
        boolean cardApprovalAbsent = cardPaymentNo != null
                && Boolean.FALSE.equals(cards.hasApprovalHistory(cardPaymentNo));
        PaymentResponse payment = commands.confirm(userId, activeToken, request, cardApprovalAbsent);

        if (payment.getMethod() == PaymentMethod.BANK_TRANSFER) {
            return accounts.issue(payment.getPaymentNo());
        }

        return payment;
    }

    /**
     * 기존 checkout 준비·소유권 검증을 로컬 트랜잭션으로 실행한다.
     */
    public CheckoutPrepareResponse prepare(String userId, String activeToken, CheckoutPrepareRequest request) {

        return commands.prepare(userId, activeToken, request);
    }
}
