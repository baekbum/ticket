package dev.bum.payment_gateway_service.feign.ticket;

import dev.bum.common.service.ticket.payment.dto.CardPaymentCompleteRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "ticket-payment-client", url = "${app.ticket.base-url}")
public interface TicketPaymentClient {

    @PostMapping("/api/v1/payments/internal/card/complete")
    PaymentResponse completeCardPayment(@RequestBody CardPaymentCompleteRequest request);
}
