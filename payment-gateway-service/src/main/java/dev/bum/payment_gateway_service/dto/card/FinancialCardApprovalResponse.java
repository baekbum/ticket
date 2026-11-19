package dev.bum.payment_gateway_service.dto.card;

import dev.bum.common.service.ticket.payment.enums.CardCompany;
import dev.bum.payment_gateway_service.jpa.card.*;
import dev.bum.payment_gateway_service.support.CardCredentials;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FinancialCardApprovalResponse(
        // 원승인의 PG 거래.
        String pgPaymentId,

        // 원승인의 예매자.
        String buyerReference,

        // 실제 승인 카드사.
        CardCompany cardCompany,

        // 같은 작업의 다른 카드 요청을 거절하기 위한 비밀값 없는 해시.
        String cardIdentity,

        // 카드사 원승인 번호. 재시도에서 재사용한다.
        String transactionId,

        // 마스킹 카드번호.
        String maskedCardNumber,

        // 원승인 금액.
        BigDecimal amount,

        // 금융 승인 시각.
        LocalDateTime approvedAt,

        // 통보 여부와 분리된 카드 금융 상태.
        CardPaymentHistoryStatus status
) {

    // 연결된 원장으로 카드 식별 해시만 계산하고 금융 결과를 반환한다.
    public static FinancialCardApprovalResponse from(DummyCardPaymentHistory history) {

        String identity = CardCredentials.hash(history.getCardCompany().name() + "|"
                + history.getDummyCard().getCardNumberHash());

        return new FinancialCardApprovalResponse(history.getPgPaymentId(), history.getUserId(),
                history.getCardCompany(), identity, history.getTransactionId(), history.getMaskedCardNumber(),
                history.getAmount(), history.getApprovedAt(), history.getStatus());
    }
}
