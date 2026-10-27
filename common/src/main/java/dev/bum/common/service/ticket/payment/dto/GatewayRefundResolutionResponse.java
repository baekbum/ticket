package dev.bum.common.service.ticket.payment.dto;

import lombok.Builder;

@Builder
public record GatewayRefundResolutionResponse(Long paymentRefundProcessId, String paymentNo, Outcome outcome) {
    public enum Outcome {
        SUCCEEDED, NOT_FOUND, REQUESTED, FAILED, CLOSED
    }
}
