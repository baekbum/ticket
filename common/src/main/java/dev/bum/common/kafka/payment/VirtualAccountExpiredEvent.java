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

    private String paymentNo;
    private BankCompany bankCompany;
    private String bankName;
    private String accountNumber;
    private BigDecimal amount;
    private LocalDateTime expiredAt;
}
