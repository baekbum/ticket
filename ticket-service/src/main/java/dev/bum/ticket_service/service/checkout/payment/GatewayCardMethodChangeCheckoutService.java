package dev.bum.ticket_service.service.checkout.payment;

import dev.bum.common.service.paymentgateway.card.*;
import dev.bum.common.service.ticket.checkout.dto.CheckoutConfirmRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayTransactionClient;
import dev.bum.ticket_service.service.checkout.CheckoutTransactionService;
import dev.bum.ticket_service.service.payment.notification.GatewayCardNotificationService;
import dev.bum.ticket_service.exception.payment.GatewayMethodChangeUnconfirmedException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class GatewayCardMethodChangeCheckoutService {

    private final CheckoutTransactionService checkouts;

    private final GatewayCardCheckoutService cards;

    private final GatewayCardMethodChangeCheckoutCommands commands;

    private final PaymentGatewayTransactionClient gateway;

    private final GatewayVirtualAccountCheckoutService accounts;

    private final GatewayCardNotificationService notifications;

    public PaymentResponse change(String userId, String activeToken, CheckoutConfirmRequest request) {

        CheckoutTransactionService.CardSwitchPreparation preparation = checkouts.prepareGatewayCardSwitch(userId, activeToken, request);

        if (preparation.request() == null) {
            if (preparation.payment().getMethod() == dev.bum.common.service.ticket.payment.enums.PaymentMethod.BANK_TRANSFER) {
                return accounts.issue(preparation.payment().getPaymentNo());
            }

            return preparation.payment();
        }

        String paymentNo = preparation.payment().getPaymentNo();
        PaymentResponse payment = preparation.payment();

        if (payment.getPgPaymentId() == null) {
            try {
                payment = cards.prepare(userId, paymentNo);
            } catch (RuntimeException failure) {
                PaymentResponse current = commands.findPayment(userId, paymentNo);

                if (current.getMethod() == dev.bum.common.service.ticket.payment.enums.PaymentMethod.BANK_TRANSFER) {
                    return accounts.issue(paymentNo);
                }

                throw failure;
            }
        }
        GatewayCardMethodChangeResponse response;

        try {
            response = gateway.changeCardToBank(payment.getPgPaymentId(), preparation.request());
        } catch (FeignException failure) {
            if (failure.status() < 0 || failure.status() >= 500) {
                throw new GatewayMethodChangeUnconfirmedException(failure);
            }

            throw failure;
        }
        commands.confirm(userId, paymentNo, preparation.request(), response);

        if ("APPROVED".equals(response.outcome())) {
            notifications.receive(response.approval());

            return commands.findPayment(userId, paymentNo);
        }

        return accounts.issue(paymentNo);
    }
}
