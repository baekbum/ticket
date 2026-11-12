package dev.bum.ticket_service.feign.paymentgateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GatewayAccountCancelResponse(
        // PG 원거래 식별자.
        String pgPaymentId,

        // Ticksy 결제번호.
        String paymentNo,

        // PG 거래와 취소 작업으로 고정한 이벤트 식별자.
        String eventId,

        // PROCESSING은 접수, CANCELLED와 REJECTED는 최종 결과.
        String status,

        // 은행의 최종 업무 사유.
        String reason,

        // 현재 PG 금융 상태.
        String pgStatus,

        // 취소 확정 시각.
        LocalDateTime cancelledAt,

        // 실제 자금 반환 여부. 이 포트폴리오에서는 false.
        boolean fundsReturned
) {
}
