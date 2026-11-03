package dev.bum.payment_gateway_service.dto.virtualAccount;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentStatus;
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
public class GatewayVirtualAccountDepositResponse {

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

    // 가상계좌 상태.
    private VirtualAccountPaymentStatus status;

    // 만료 시각.
    private LocalDateTime expiresAt;

    // 입금 완료 시각.
    private LocalDateTime depositedAt;

    // 처리 결과 메시지.
    private String message;
}
