package dev.bum.common.service.paymentgateway.refund;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record GatewayBankRefundRequest(
        // 인증한 가맹점과 교차 검증할 숫자 코드.
        Long merchantId,

        // 원거래의 구매자 참조값.
        @NotBlank String buyerReference,

        // 최초 스냅샷에서 확정한 금액.
        @NotNull @Positive BigDecimal amount,

        // 환불받을 은행.
        @NotNull BankCompany refundBankCompany,

        // 전송 중에만 사용하는 환불 계좌 원문.
        @NotBlank @Size(max = 50) String refundAccountNumber,

        // 환불 계좌 예금주.
        @NotBlank @Size(max = 50) String refundAccountHolder
) {

    // 은행에는 가맹점 업무 정보 없이 금융 처리 조건만 전달한다.
    public BankRefundCommand toBank(String paymentId, String refundId) {

        return new BankRefundCommand(paymentId, refundId, amount, refundBankCompany, refundAccountNumber, refundAccountHolder);
    }

    // 원문 계좌 정보의 로그 노출을 막는다.
    @Override
    public String toString() {

        return "GatewayBankRefundRequest[merchantId=" + merchantId + "]";
    }
}
