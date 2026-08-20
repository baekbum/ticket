package dev.bum.ticket_service.service.checkout.payment;

import dev.bum.common.service.ticket.checkout.dto.CheckoutConfirmRequest;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.ticket_service.jpa.payment.Payment;
import org.springframework.stereotype.Service;

@Service
public class CardCheckoutPaymentHandler implements CheckoutPaymentHandler {

    @Override
    public PaymentMethod paymentMethod() {
        return PaymentMethod.CREDIT_CARD;
    }

    @Override
    public void process(CheckoutConfirmRequest request, Payment payment) {
    }
}
