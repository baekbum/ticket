package dev.bum.ticket_service.service.checkout.payment.translate;

import dev.bum.ticket_service.service.checkout.payment.card.CardCheckoutService;
import dev.bum.ticket_service.service.checkout.payment.virtualAccount.VirtualAccountIssuanceCheckoutService;

import dev.bum.common.service.paymentgateway.card.*;
import dev.bum.common.service.ticket.checkout.dto.CheckoutConfirmRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.ticket_service.exception.payment.GatewayMethodChangeUnconfirmedException;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayTransactionClient;
import dev.bum.ticket_service.service.checkout.CheckoutTransactionService;
import dev.bum.ticket_service.service.payment.notification.CardPaymentNotificationService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class CardToBankCheckoutService {

    private final CheckoutTransactionService checkouts;

    private final CardCheckoutService cards;

    private final CheckoutMethodChangeCommandService commands;

    private final PaymentGatewayTransactionClient gateway;

    private final VirtualAccountIssuanceCheckoutService accounts;

    private final CardPaymentNotificationService notifications;

    public PaymentResponse change(String userId, String activeToken, CheckoutConfirmRequest request) {

        CheckoutTransactionService.CardToBankPreparation preparation = checkouts.prepareCardToBankChange(userId, activeToken, request);

        if (preparation.request() == null) {
            return replay(preparation.payment());
        }

        GatewayCardMethodChangeResponse response;

        try {
            PaymentResponse payment = prepareGatewayPayment(userId, preparation.payment());

            if (payment.getMethod() == PaymentMethod.BANK_TRANSFER) {
                return replay(payment);
            }

            response = requestMethodChange(payment.getPgPaymentId(), preparation.request());
        } catch (FeignException failure) {
            if (failure.status() < 0 || failure.status() >= 500) {
                throw new GatewayMethodChangeUnconfirmedException(failure);
            }

            throw failure;
        }

        return completeMethodChange(userId, preparation.payment().getPaymentNo(), preparation.request(), response);
    }

    // 최초 카드 준비 조건을 유지하며, 이미 연결된 거래는 다시 prepare하지 않는다.
    private PaymentResponse prepareGatewayPayment(String userId, PaymentResponse payment) {

        if (payment.getPgPaymentId() != null) {
            return payment;
        }

        try {
            return cards.prepare(userId, payment.getPaymentNo());
        } catch (RuntimeException failure) {
            PaymentResponse current = commands.findPayment(userId, payment.getPaymentNo());

            if (current.getMethod() == PaymentMethod.BANK_TRANSFER) {
                return current;
            }

            throw failure;
        }
    }

    private GatewayCardMethodChangeResponse requestMethodChange(String pgPaymentId, GatewayCardMethodChangeRequest request) {

        return gateway.changeCardToBank(pgPaymentId, request);
    }

    private PaymentResponse completeMethodChange(String userId, String paymentNo,
            GatewayCardMethodChangeRequest request, GatewayCardMethodChangeResponse response) {

        commands.confirmCardToBank(userId, paymentNo, request, response);

        if ("APPROVED".equals(response.outcome())) {
            notifications.receive(response.approval());

            return commands.findPayment(userId, paymentNo);
        }

        PaymentResponse prepared = accounts.prepare(paymentNo);

        return accounts.issue(prepared.getPaymentNo());
    }

    private PaymentResponse replay(PaymentResponse payment) {

        if (payment.getMethod() != PaymentMethod.BANK_TRANSFER) {
            return payment;
        }

        PaymentResponse prepared = accounts.prepare(payment.getPaymentNo());

        return accounts.issue(prepared.getPaymentNo());
    }
}
