package dev.bum.ticket_service.controller.payment;

import dev.bum.common.service.ticket.payment.dto.CompletePaymentRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.ticket_service.service.payment.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/confirm")
    public ResponseEntity<PaymentResponse> confirm(@Valid @RequestBody CompletePaymentRequest request) {
        return ResponseEntity.ok(paymentService.confirm(request));
    }
}
