package dev.bum.ticket_service.feign.paymentgateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GatewayPaymentPrepareResponse(
        // PG에서 발급한 문자열 거래 식별자.
        String pgPaymentId,

        // 인증된 소유 가맹점 코드.
        Long merchantId,

        // Ticksy 결제번호.
        String paymentNo,

        // 최초 준비의 예매자.
        String buyerReference,

        // 저장된 결제수단.
        PaymentMethod paymentMethod,

        // 저장된 은행.
        BankCompany bankCompany,

        // 저장된 금액.
        BigDecimal amount,

        // 저장된 입금기한.
        LocalDateTime expiresAt,

        // PG 금융 거래 상태.
        String status
) {
}
