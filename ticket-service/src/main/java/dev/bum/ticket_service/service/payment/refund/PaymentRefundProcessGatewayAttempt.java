package dev.bum.ticket_service.service.payment.refund;

import dev.bum.ticket_service.service.reservation.reservation.CancellationFeeCalculator.CancellationRefundCalculation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class PaymentRefundProcessGatewayAttempt {

    private Long paymentRefundProcessId;
    private boolean gatewayRequired;
    private boolean localPaymentRefundRequired;
    private CancellationRefundCalculation calculation;
    private boolean previousRequestRecoveryRequired;

    public PaymentRefundProcessGatewayAttempt(Long id, boolean gatewayRequired, boolean localRequired,
                                             CancellationRefundCalculation calculation) {
        this(id, gatewayRequired, localRequired, calculation, false);
    }
}
