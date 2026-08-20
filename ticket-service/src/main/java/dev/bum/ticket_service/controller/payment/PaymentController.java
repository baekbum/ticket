package dev.bum.ticket_service.controller.payment;

import dev.bum.common.service.ticket.payment.dto.CardPaymentCompleteRequest;
import dev.bum.common.service.ticket.payment.dto.CardPaymentFailRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountDepositCompleteRequest;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountIssuedRequest;
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

    @PostMapping("/internal/card/complete")
    public ResponseEntity<PaymentResponse> completeCardFromGateway(
            @Valid @RequestBody CardPaymentCompleteRequest request
    ) {
        return ResponseEntity.ok(paymentService.completeCardFromGateway(request));
    }

    @PostMapping("/internal/card/fail")
    public ResponseEntity<PaymentResponse> failCardFromGateway(
            @Valid @RequestBody CardPaymentFailRequest request
    ) {
        return ResponseEntity.ok(paymentService.failCardFromGateway(request));
    }

    @PostMapping("/internal/virtual-account/issued")
    public ResponseEntity<PaymentResponse> applyVirtualAccountIssued(
            @Valid @RequestBody VirtualAccountIssuedRequest request
    ) {
        return ResponseEntity.ok(paymentService.applyVirtualAccountIssued(request));
    }

    @PostMapping("/internal/virtual-account/deposit/complete")
    public ResponseEntity<PaymentResponse> completeVirtualAccountDepositFromGateway(
            @Valid @RequestBody VirtualAccountDepositCompleteRequest request
    ) {
        return ResponseEntity.ok(paymentService.completeVirtualAccountDepositFromGateway(request));
    }
}
