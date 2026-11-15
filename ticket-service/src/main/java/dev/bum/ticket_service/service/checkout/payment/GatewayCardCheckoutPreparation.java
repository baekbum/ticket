package dev.bum.ticket_service.service.checkout.payment;

import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.ticket_service.feign.paymentgateway.GatewayPaymentPrepareRequest;

public record GatewayCardCheckoutPreparation(
        GatewayPaymentPrepareRequest request,
        String pgPaymentId,
        PaymentResponse payment
) { }
