package dev.bum.ticket_service.feign.paymentgateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GatewayPaymentPrepareRequest(
        // 서버 설정의 가맹점 코드. 인증 수단으로 사용하지 않는다.
        Long merchantId,

        // Ticksy에서 최초 저장한 결제번호.
        String paymentNo,

        // Ticksy가 확인한 예매 소유자.
        String buyerReference,

        // 최초 요청의 결제수단.
        PaymentMethod paymentMethod,

        // 스냅샷에 고정한 결제 금액.
        BigDecimal amount,

        // Ticksy 업무 규칙으로 결정한 입금기한.
        LocalDateTime expiresAt,

        // 최초 선택한 은행.
        BankCompany bankCompany
) {
}
