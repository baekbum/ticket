package dev.bum.ticket_service.service.payment.refund;

import dev.bum.common.service.ticket.payment.dto.PaymentRefundProcessResponse;

public record GatewayRefundActionResponse(
        // SUCCEEDED 또는 WITHDRAWN. 이미 성공한 요청의 철회도 SUCCEEDED다.
        String outcome,

        // 마스킹된 최초 신청과 현재 진행 상태.
        PaymentRefundProcessResponse application,

        // 상태 반영 커밋 후 화면에 안내할 메시지.
        String message
) { }
