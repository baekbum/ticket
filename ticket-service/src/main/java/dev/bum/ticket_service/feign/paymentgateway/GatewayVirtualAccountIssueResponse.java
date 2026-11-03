package dev.bum.ticket_service.feign.paymentgateway;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.GatewayVirtualAccountStatus;
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
public class GatewayVirtualAccountIssueResponse {

    // 결제 번호.
    private String paymentNo;

    // 은행 구분.
    private BankCompany bankCompany;

    // 은행 이름.
    private String bankName;

    // 계좌 번호.
    private String accountNumber;

    // 입금자 이름.
    private String depositorName;

    // 금액.
    private BigDecimal amount;

    // 만료 시각.
    private LocalDateTime expiresAt;

    // 가상계좌 상태.
    private GatewayVirtualAccountStatus status;

    // 발급 완료 여부.
    private Boolean issued;

    // 처리 결과 메시지.
    private String message;
}
