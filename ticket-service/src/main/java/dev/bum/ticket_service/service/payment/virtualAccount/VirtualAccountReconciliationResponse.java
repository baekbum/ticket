package dev.bum.ticket_service.service.payment.virtualAccount;

import dev.bum.common.service.ticket.payment.dto.GatewayVirtualAccountStatusResponse;
import dev.bum.common.service.ticket.payment.dto.PaymentResponse;

public record VirtualAccountReconciliationResponse(
        // 결제 정보.
        PaymentResponse payment,

        // 결제 게이트웨이 처리 결과.
        GatewayVirtualAccountStatusResponse gateway,

        // 완료 처리 가능 여부.
        boolean completable,

        // 처리 결과 메시지.
        String message
) {
}
