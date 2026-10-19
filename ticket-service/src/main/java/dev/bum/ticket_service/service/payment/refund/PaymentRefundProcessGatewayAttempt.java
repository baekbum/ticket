package dev.bum.ticket_service.service.payment.refund;

import dev.bum.ticket_service.service.reservation.reservation.CancellationFeeCalculator.CancellationRefundCalculation;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

@Getter
@Builder
public class PaymentRefundProcessGatewayAttempt {

    @NonNull
    private final Long paymentRefundProcessId;
    @NonNull
    private final RefundAction action;
    @NonNull
    private final CancellationRefundCalculation calculation;
}
