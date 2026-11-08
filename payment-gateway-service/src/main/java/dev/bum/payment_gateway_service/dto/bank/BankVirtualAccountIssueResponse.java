package dev.bum.payment_gateway_service.dto.bank;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccount;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BankVirtualAccountIssueResponse(
        // 원래 발급 요청의 PG 거래 식별자.
        String pgPaymentId,

        // 발급된 은행.
        BankCompany bankCompany,

        // 발급 시 저장한 은행 이름.
        String bankName,

        // 실제 발급된 계좌번호.
        String accountNumber,

        // 입금받을 금액.
        BigDecimal amount,

        // 저장한 입금 기한.
        LocalDateTime expiresAt,

        // 은행의 계좌 상태. PG 상태와 구분한다.
        VirtualAccountPaymentStatus status
) {

    // 영속 엔티티를 외부에 노출하지 않고 저장된 발급 결과를 반환한다.
    public static BankVirtualAccountIssueResponse from(DummyVirtualAccount account) {

        return new BankVirtualAccountIssueResponse(account.getPgPaymentId(), account.getBankCompany(),
                account.getBankName(), account.getAccountNumber(), account.getAmount(), account.getExpiresAt(),
                account.getStatus());
    }
}
