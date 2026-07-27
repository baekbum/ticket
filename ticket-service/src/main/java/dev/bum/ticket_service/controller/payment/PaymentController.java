package dev.bum.ticket_service.controller.payment;

import dev.bum.common.service.ticket.payment.dto.CardPaymentApproveRequest;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountDepositRequest;
import dev.bum.common.service.ticket.payment.dto.VirtualAccountIssueRequest;
import dev.bum.ticket_service.service.payment.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/card/approve")
    public ResponseEntity<PaymentResponse> approveCard(
            @AuthenticationPrincipal String currentUserId,
            @RequestHeader(value = "X-Queue-Token", required = false) String queueToken,
            @Valid @RequestBody CardPaymentApproveRequest request
    ) {
        return ResponseEntity.ok(paymentService.approveCard(currentUserId, queueToken, request));
    }

    @PostMapping("/virtual-account/issue")
    public ResponseEntity<PaymentResponse> issueVirtualAccount(
            @AuthenticationPrincipal String currentUserId,
            @RequestHeader(value = "X-Queue-Token", required = false) String queueToken,
            @Valid @RequestBody VirtualAccountIssueRequest request
    ) {
        return ResponseEntity.ok(paymentService.issueVirtualAccount(currentUserId, queueToken, request));
    }

    @PostMapping("/virtual-account/deposit")
    public ResponseEntity<PaymentResponse> depositVirtualAccount(
            @Valid @RequestBody VirtualAccountDepositRequest request
    ) {
        return ResponseEntity.ok(paymentService.depositVirtualAccount(request));
    }
}
