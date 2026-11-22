package dev.bum.ticket_service.service.payment.refund;

import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayRefundApplicationClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PaymentRefundReconciliationService {
    private final PaymentRefundProcessService processes;
    private final PaymentGatewayRefundApplicationClient gateway;

    public enum Outcome { APPLIED, NOT_FOUND, REQUESTED, WITHDRAWN, NO_GATEWAY_PAYMENT }

    public Outcome reconcile(Long processId) {
        var target = processes.gatewayCheckTarget(processId);
        if (target.alreadyApplied()) return Outcome.APPLIED;
        if (target.zeroRefund()) {
            processes.applyVerifiedGatewayRefund(processId, null);
            return Outcome.APPLIED;
        }
        if (!StringUtils.hasText(target.pgPaymentId())) return Outcome.NO_GATEWAY_PAYMENT;

        // External lookup runs before the local reservation lock is acquired.
        String requestId = processId.toString();
        var result = gateway.status(target.pgPaymentId(), requestId);
        if (result == null) return Outcome.NOT_FOUND;
        if (!target.pgPaymentId().equals(result.pgPaymentId()) || !requestId.equals(result.refundRequestId())) {
            throw new IllegalStateException("PG 환불 조회 결과가 원래 신청과 일치하지 않습니다.");
        }
        return switch (result.outcome()) {
            case "SUCCEEDED" -> {
                processes.applyVerifiedGatewayRefund(processId, result);
                yield Outcome.APPLIED;
            }
            case "REQUESTED" -> Outcome.REQUESTED;
            case "WITHDRAWN" -> Outcome.WITHDRAWN;
            default -> throw new IllegalStateException("알 수 없는 PG 환불 상태입니다.");
        };
    }
}
