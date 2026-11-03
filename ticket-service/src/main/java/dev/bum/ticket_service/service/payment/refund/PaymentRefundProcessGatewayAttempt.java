package dev.bum.ticket_service.service.payment.refund;

import dev.bum.ticket_service.service.reservation.reservation.CancellationFeeCalculator.CancellationRefundCalculation;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

@Getter
@Builder
public class PaymentRefundProcessGatewayAttempt {

    // 결제 환불 처리 식별자.
    @NonNull
    private final Long paymentRefundProcessId;
    // 수행한 작업.
    @NonNull
    private final RefundAction action;
    // 환불 금액과 공제 금액의 계산 결과.
    @NonNull
    private final CancellationRefundCalculation calculation;
}
