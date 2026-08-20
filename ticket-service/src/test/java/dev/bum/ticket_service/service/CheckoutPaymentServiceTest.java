package dev.bum.ticket_service.service;

import dev.bum.common.service.ticket.checkout.dto.CheckoutConfirmRequest;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.service.checkout.payment.CardCheckoutPaymentHandler;
import dev.bum.ticket_service.service.checkout.payment.CheckoutPaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutPaymentServiceTest {

    @Test
    @DisplayName("결제수단에 맞는 checkout 결제 handler를 실행한다")
    void process_payment_handler_by_payment_method() {
        CheckoutPaymentService checkoutPaymentService = new CheckoutPaymentService(
                List.of(new CardCheckoutPaymentHandler())
        );
        Payment payment = Payment.builder()
                .method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.READY)
                .build();
        CheckoutConfirmRequest request = CheckoutConfirmRequest.builder()
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        checkoutPaymentService.process(request, payment);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
    }
}
