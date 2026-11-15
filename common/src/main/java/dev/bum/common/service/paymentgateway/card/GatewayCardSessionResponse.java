package dev.bum.common.service.paymentgateway.card;

import java.math.BigDecimal;
import java.time.Instant;

public record GatewayCardSessionResponse(
        // 세션을 발급한 거래의 소유 가맹점.
        Long merchantId,

        // 재시도·조회에서도 바꾸지 않는 PG 거래 식별자.
        String pgPaymentId,

        // 가맹점 원결제번호.
        String paymentNo,

        // 사용자 JWT와 대조할 구매자.
        String buyerReference,

        // 원거래에 고정한 승인 금액.
        BigDecimal amount,

        // 브라우저 메모리에만 보관할 거래 전용 세션. 가맹점 인증 키가 아니다.
        String paymentSession,

        // 세션 자체의 만료 시각. 금융 거래의 만료와 구분한다.
        Instant expiresAt,

        // false이면 조회 전용이며 새 승인 요청을 보낼 수 없다.
        boolean canApprove
) {

    @Override
    public String toString() {

        return "GatewayCardSessionResponse[pgPaymentId=" + pgPaymentId + ", paymentSession=REDACTED]";
    }
}
