package dev.bum.ticket_service.service.payment;

import dev.bum.common.service.ticket.payment.dto.GatewayVirtualAccountStatusResponse;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;

public record VirtualAccountReconciliationResponse(
        PaymentResponse payment,
        GatewayVirtualAccountStatusResponse gateway,
        boolean completable,
        String message
) {
}
