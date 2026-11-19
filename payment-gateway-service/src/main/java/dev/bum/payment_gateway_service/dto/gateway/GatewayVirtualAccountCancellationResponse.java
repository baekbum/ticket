package dev.bum.payment_gateway_service.dto.gateway;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.bum.payment_gateway_service.jpa.gateway.GatewayPaymentStatus;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GatewayVirtualAccountCancellationResponse(
        // PG 원거래.
        String pgPaymentId,

        // 소유 가맹점의 결제번호.
        String paymentNo,

        // 최초 취소 요청의 고정 멱등 식별자.
        String eventId,

        // PROCESSING/CANCELLED/REJECTED. 접수와 취소 완료를 구분한다.
        String status,

        // 은행의 최종 업무 결과 사유. 처리 중이면 null이다.
        String reason,

        // 현재 PG 거래 상태. 요청의 전달 상태와 별개다.
        GatewayPaymentStatus pgStatus,

        // 최초 PG 취소 확정 시각. 처리 중이거나 거절되면 없다.
        LocalDateTime cancelledAt,

        // 실제 자금 반환을 수행하지 않는다.
        boolean fundsReturned
) {

    // 최초 접수와 확정된 동일 키 재요청을 HTTP 상태로 구분한다.
    public boolean completed() {

        return !"PROCESSING".equals(status);
    }
}
