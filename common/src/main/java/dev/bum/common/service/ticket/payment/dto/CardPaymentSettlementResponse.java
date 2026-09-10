package dev.bum.common.service.ticket.payment.dto;

/** ticket에서 결제를 완료할 수 없는 상태가 확정되어 저장된 경우에만 REJECTED를 반환한다. */
public record CardPaymentSettlementResponse(Outcome outcome, PaymentResponse payment) {
    public enum Outcome { COMPLETED, REJECTED }
}
