package dev.bum.common.kafka.payment;

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
public class VirtualAccountExpiredEvent {

    // 결제 번호.
    private String paymentNo;

    // 은행 구분.
    private BankCompany bankCompany;

    // 은행 이름.
    private String bankName;

    // 계좌 번호.
    private String accountNumber;

    // 금액.
    private BigDecimal amount;

    // 만료 처리 시각.
    private LocalDateTime expiredAt;
}
