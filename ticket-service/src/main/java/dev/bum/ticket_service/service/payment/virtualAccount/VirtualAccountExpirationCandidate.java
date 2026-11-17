package dev.bum.ticket_service.service.payment.virtualAccount;

public record VirtualAccountExpirationCandidate(
        // Ticksy에서 기한이 지난 미완료 결제.
        Long paymentId,

        // 가맹점 안의 원거래 결제번호.
        String paymentNo,

        // 준비 응답 저장 전 중단된 경우 null이다. 이때 가맹점 결제번호로 읽기 전용 조회한다.
        String pgPaymentId
) {
}
