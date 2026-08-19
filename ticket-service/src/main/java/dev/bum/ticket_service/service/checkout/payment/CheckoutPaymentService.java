package dev.bum.ticket_service.service.checkout.payment;

import dev.bum.common.service.ticket.checkout.dto.CheckoutConfirmRequest;
import dev.bum.ticket_service.jpa.payment.Payment;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CheckoutPaymentService {

    private final List<CheckoutPaymentHandler> paymentHandlers;

    public CheckoutPaymentService(List<CheckoutPaymentHandler> paymentHandlers) {
        this.paymentHandlers = paymentHandlers;
    }

    public void process(CheckoutConfirmRequest request, Payment payment) {
        CheckoutPaymentHandler paymentHandler = paymentHandlers.stream()
                .filter(handler -> handler.paymentMethod() == request.getPaymentMethod())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 결제 수단입니다."));

        paymentHandler.process(request, payment);
    }
}
