package dev.bum.ticket_service.service.checkout.payment;

import dev.bum.common.service.ticket.checkout.dto.CheckoutConfirmRequest;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.ticket_service.jpa.payment.Payment;

public interface CheckoutPaymentHandler {

    PaymentMethod paymentMethod();

    void process(CheckoutConfirmRequest request, Payment payment);
}
