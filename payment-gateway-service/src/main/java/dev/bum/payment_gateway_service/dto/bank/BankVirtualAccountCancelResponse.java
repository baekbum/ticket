package dev.bum.payment_gateway_service.dto.bank;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.payment_gateway_service.jpa.virtualAccount.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BankVirtualAccountCancelResponse(
        // 취소 계좌가 참조하는 PG 거래.
        String pgPaymentId,

        // 발급 은행.
        BankCompany bankCompany,

        // 최초 발급 은행명.
        String bankName,

        // 실제 취소 계좌.
        String accountNumber,

        // 원거래 금액.
        BigDecimal amount,

        // 최초 입금 기한.
        LocalDateTime expiresAt,

        // 은행 상태. 취소 증거로는 CANCELLED만 사용한다.
        VirtualAccountPaymentStatus status,

        // 기존 입금이 있었다면 보존하는 최초 입금자.
        String depositorName,

        // 기존 입금이 있었다면 보존하는 최초 입금 시각.
        LocalDateTime depositedAt,

        // 은행이 확정한 취소 사유. 입금/취소 가능 여부는 은행에서 판단한다.
        String reason
) {

    // PG가 요청해 은행 원장에 확정한 취소의 고정 사유 코드.
    public static final String REQUESTED_REASON = "PG_MERCHANT_CANCELLED";

    // JSONB가 숫자 표현을 정규화해도 최초 응답과 재응답의 금액 표현을 유지한다.
    public BankVirtualAccountCancelResponse {

        if (amount != null) {
            amount = amount.setScale(2, java.math.RoundingMode.UNNECESSARY);
        }
    }

    // 은행 원장 사실만 복사하고 가맹점 업무 상태를 추정하지 않는다.
    public static BankVirtualAccountCancelResponse from(DummyVirtualAccount account) {

        return new BankVirtualAccountCancelResponse(account.getPgPaymentId(), account.getBankCompany(),
                account.getBankName(), account.getAccountNumber(), account.getAmount(), account.getExpiresAt(),
                account.getStatus(), account.getDepositorName(), account.getDepositedAt(), account.getFailureReason());
    }
}
