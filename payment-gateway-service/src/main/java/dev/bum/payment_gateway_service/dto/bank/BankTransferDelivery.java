package dev.bum.payment_gateway_service.dto.bank;

import dev.bum.payment_gateway_service.jpa.outbox.BankTransferOutbox;

import java.util.Map;

public record BankTransferDelivery(
        // 전달 행의 식별자.
        Long outboxId,

        // 이번 워커가 담당하는 전달 단계.
        BankTransferOutbox.Flow flow,

        // 재시도에도 같은 업무 이벤트 식별자.
        String eventId,

        // 연결된 PG 거래.
        String pgPaymentId,

        // 최초 등록된 전송 내용.
        Map<String, Object> payload,

        // 이 임대만 완료할 수 있는 시도 번호.
        int attempt
) { }
