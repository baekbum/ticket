package dev.bum.payment_gateway_service.dto.bank;

public record BankVirtualAccountCancellationResult(
        // 거래·취소 작업에 고정된 멱등 식별자.
        String eventId,

        // 은행이 실제 처리한 최초 입력. 같은 키의 다른 요청과 구분한다.
        BankVirtualAccountCancelRequest request,

        // CANCELLED 또는 REJECTED. HTTP 전달 성공과 별개의 업무 결과.
        String outcome,

        // 확정 거절 또는 취소 사유. 외부 예외 본문을 그대로 저장하지 않는다.
        String reason,

        // 처리 당시 은행 원장의 스냅샷. 미발급 거절이면 없다.
        BankVirtualAccountCancelResponse account
) {

    // 다른 거래 응답·빈 성공·성공으로 위장한 거절을 PG가 수용하지 않게 한다.
    public boolean matches(BankVirtualAccountCancelRequest expected) {

        return (expected.pgPaymentId() + ":CANCEL:INITIAL").equals(eventId) && expected.equals(request)
                && ("CANCELLED".equals(outcome) || "REJECTED".equals(outcome))
                && reason != null && !reason.isBlank();
    }
}
