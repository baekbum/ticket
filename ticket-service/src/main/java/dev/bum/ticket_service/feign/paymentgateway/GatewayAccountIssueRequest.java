package dev.bum.ticket_service.feign.paymentgateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GatewayAccountIssueRequest(
        // 서버 설정의 가맹점 코드.
        Long merchantId,

        // 예매 소유자.
        String buyerReference,

        // 최초 준비와 같은 금액.
        BigDecimal amount,

        // 최초 준비와 같은 은행.
        BankCompany bankCompany
) {
}
