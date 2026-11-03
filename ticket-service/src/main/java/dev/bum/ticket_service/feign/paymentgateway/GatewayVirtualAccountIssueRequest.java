package dev.bum.ticket_service.feign.paymentgateway;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayVirtualAccountIssueRequest {

    // 결제 번호.
    private String paymentNo;

    // 은행 구분.
    private BankCompany bankCompany;

    // 금액.
    private BigDecimal amount;

    // 공연 시작 일시.
    private LocalDateTime eventDateTime;
}
