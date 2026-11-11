package dev.bum.ticket_service.controller.payment;

import dev.bum.common.service.paymentgateway.notification.*;
import dev.bum.ticket_service.service.payment.notification.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments/internal/gateway")
@RequiredArgsConstructor
public class GatewayPaymentNotificationController {

    private final GatewayPaymentNotificationService notifications;
    private final GatewayBankCancellationNotificationService cancellations;

    @PostMapping(value = "/notifications", headers = "X-PG-Event-Type=BANK_CANCELLATION")
    public ResponseEntity<BankCancellationNotificationResponse> cancellation(
            @Valid @RequestBody BankCancellationNotification notification
    ) {

        return ResponseEntity.ok(cancellations.receive(notification));
    }

    @PostMapping("/notifications")
    public ResponseEntity<?> receive(@Valid @RequestBody GatewayPaymentNotification notification) {

        return ResponseEntity.ok(notifications.receive(notification));
    }
}
