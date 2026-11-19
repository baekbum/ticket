package dev.bum.ticket_service.feign.paymentgateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GatewayAccountCancelRequest(
        // 서버 설정의 소유 가맹점.
        Long merchantId,

        // 최초 예매 소유자.
        String buyerReference,

        // 원거래의 고정 금액.
        BigDecimal amount,

        // 저장된 계좌의 은행.
        BankCompany bankCompany,

        // 최초 발급된 계좌.
        String accountNumber
) {
}
