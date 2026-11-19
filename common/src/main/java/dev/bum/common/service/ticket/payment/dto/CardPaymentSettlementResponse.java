package dev.bum.common.service.ticket.payment.dto;

/** ticket에서 결제를 완료할 수 없는 상태가 확정되어 저장된 경우에만 REJECTED를 반환한다. */
public record CardPaymentSettlementResponse(
        // 작업 수행 결과.
        Outcome outcome,

        // 결제 정보.
        PaymentResponse payment
) {

    public enum Outcome { // 처리 완료 결과.
        COMPLETED, // 요청 거절 결과.

        REJECTED
    }
}
