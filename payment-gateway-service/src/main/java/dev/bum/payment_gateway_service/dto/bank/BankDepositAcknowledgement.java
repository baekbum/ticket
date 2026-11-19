package dev.bum.payment_gateway_service.dto.bank;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BankDepositAcknowledgement(
        // PG가 DB 커밋을 끝낸 최초 입금 이벤트.
        String eventId,

        // PG가 수신한 원거래.
        String pgPaymentId,

        // PG가 수신한 계좌.
        String accountNumber,

        // PG가 수신한 최초 금액.
        BigDecimal amount,

        // PG가 수신한 최초 입금 시각.
        LocalDateTime depositedAt
) {

    // 가맹점 전달 완료가 아닌 PG 수신 커밋만 확인한다.
    public static BankDepositAcknowledgement of(BankDepositEvent event) {

        BankVirtualAccountDepositResponse bank = event.deposit();

        return new BankDepositAcknowledgement(event.eventId(), bank.pgPaymentId(), bank.accountNumber(),
                bank.amount(), bank.depositedAt());
    }

    // 다른 거래나 빈 성공 응답을 전달 완료로 인정하지 않는다.
    public boolean matches(BankDepositEvent event) {

        BankVirtualAccountDepositResponse bank = event.deposit();

        return event.eventId().equals(eventId) && bank.pgPaymentId().equals(pgPaymentId)
                && bank.accountNumber().equals(accountNumber) && amount != null && bank.amount().compareTo(amount) == 0
                && bank.depositedAt().equals(depositedAt);
    }
}
