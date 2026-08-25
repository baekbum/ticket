package dev.bum.ticket_service.service.payment;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentRefundProcessGatewayAttempt {

    private Long paymentRefundProcessId;
    private boolean gatewayRequired;
    private boolean localPaymentRefundRequired;
}
