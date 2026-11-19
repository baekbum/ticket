package dev.bum.ticket_service.feign.paymentgateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GatewayAccountIssueResponse(
        // 발급한 계좌에 연결된 PG 거래.
        String pgPaymentId,

        // 거래 소유 가맹점.
        Long merchantId,

        // Ticksy 원거래 결제번호.
        String paymentNo,

        // 예매 소유자.
        String buyerReference,

        // 발급 은행.
        BankCompany bankCompany,

        // 발급 은행 이름.
        String bankName,

        // 최초 발급한 계좌번호.
        String accountNumber,

        // 원거래 금액.
        BigDecimal amount,

        // 최초 입금기한.
        LocalDateTime expiresAt,

        // 응답 시점 PG 상태.
        String pgStatus,

        // 최초 발급 결과의 은행 상태.
        String bankStatus
) {
}
