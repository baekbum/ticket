package dev.bum.payment_gateway_service.dto.bank;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.payment_gateway_service.jpa.virtualAccount.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BankVirtualAccountDepositResponse(
        // 입금된 계좌가 참조하는 PG 원거래.
        String pgPaymentId,

        // 최초 발급 은행.
        BankCompany bankCompany,

        // 발급 은행명 스냅샷.
        String bankName,

        // 실제 입금된 계좌.
        String accountNumber,

        // 최초 입금 금액. 취소나 재시도로 변경하지 않는다.
        BigDecimal amount,

        // 발급 당시 일반적인 입금 기한.
        LocalDateTime expiresAt,

        // 최초 입금자.
        String depositorName,

        // 최초 입금 시각. 응답 유실 후에도 동일한 시각을 반환한다.
        LocalDateTime depositedAt,

        // 은행 상태. CANCELLED여도 입금 사실과 실제 자금 반환은 별개다.
        VirtualAccountPaymentStatus status
) {

    // 금융 사실을 불변 응답으로 복사한다.
    public static BankVirtualAccountDepositResponse from(DummyVirtualAccount account) {

        return new BankVirtualAccountDepositResponse(account.getPgPaymentId(), account.getBankCompany(),
                account.getBankName(), account.getAccountNumber(), account.getAmount(), account.getExpiresAt(),
                account.getDepositorName(), account.getDepositedAt(), account.getStatus());
    }
}
