package dev.bum.ticket_service.controller.payment;

import dev.bum.common.service.paymentgateway.notification.CardPaymentNotification;
import dev.bum.common.service.paymentgateway.notification.CardPaymentNotificationResponse;
import dev.bum.ticket_service.service.payment.notification.GatewayCardNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments/internal/gateway/card")
@RequiredArgsConstructor
public class GatewayCardNotificationController {

    private final GatewayCardNotificationService notifications;

    @PostMapping("/notifications")
    public ResponseEntity<CardPaymentNotificationResponse> receive(
            @Valid @RequestBody CardPaymentNotification notification
    ) {

        return ResponseEntity.ok(notifications.receive(notification));
    }
}
