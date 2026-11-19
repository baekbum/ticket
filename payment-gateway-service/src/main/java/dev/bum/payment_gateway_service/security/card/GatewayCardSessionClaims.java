package dev.bum.payment_gateway_service.security.card;

public record GatewayCardSessionClaims(
        Long merchantId,
        String pgPaymentId,
        String paymentNo,
        String buyerReference,
        int methodRevision,
        boolean canApprove
) { }
