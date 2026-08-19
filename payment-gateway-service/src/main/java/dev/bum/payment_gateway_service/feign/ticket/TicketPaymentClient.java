package dev.bum.payment_gateway_service.feign.ticket;

import dev.bum.common.service.ticket.payment.dto.CardPaymentCompleteRequest;
import dev.bum.common.service.ticket.payment.dto.CardPaymentFailRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountIssuedRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "ticket-payment-client", url = "${app.ticket.base-url}")
public interface TicketPaymentClient {

    @PostMapping("/api/v1/payments/internal/card/complete")
    PaymentResponse completeCardPayment(@RequestBody CardPaymentCompleteRequest request);

    @PostMapping("/api/v1/payments/internal/card/fail")
    PaymentResponse failCardPayment(@RequestBody CardPaymentFailRequest request);

    @PostMapping("/api/v1/payments/internal/virtual-account/issued")
    PaymentResponse applyVirtualAccountIssued(@RequestBody VirtualAccountIssuedRequest request);
}
