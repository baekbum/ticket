package dev.bum.common.service.paymentgateway.refund;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record GatewayPaymentRefundRequest(
        // 인증된 가맹점과 교차 검증할 서버의 가맹점 코드.
        Long merchantId,

        // PG 원거래와 대조할 구매자 참조값.
        @NotBlank @Size(max = 50) String buyerReference,

        // 최초 환불 신청 스냅샷에서 확정한 금액.
        @NotNull @Positive BigDecimal amount,

        // 무통장 환불에만 필요한 은행. 카드 환불에는 전달하지 않는다.
        BankCompany refundBankCompany,

        // 무통장 환불에만 필요한 계좌 원문.
        @Size(max = 50) String refundAccountNumber,

        // 무통장 환불에만 필요한 예금주.
        @Size(max = 50) String refundAccountHolder
) {

    public GatewayBankRefundRequest bankRequest() {

        return new GatewayBankRefundRequest(merchantId, buyerReference, amount,
                refundBankCompany, refundAccountNumber, refundAccountHolder);
    }

    public BankRefundCommand toBank(String paymentId, String refundId) {

        return bankRequest().toBank(paymentId, refundId);
    }

    public CardRefundCommand toCard(String paymentId, String refundId) {

        if (refundBankCompany != null || refundAccountNumber != null || refundAccountHolder != null) {
            throw new IllegalArgumentException("카드 환불에는 환불 계좌를 전달할 수 없습니다.");
        }

        return new CardRefundCommand(paymentId, refundId, amount);
    }

    @Override
    public String toString() {

        return "GatewayPaymentRefundRequest[merchantId=" + merchantId + "]";
    }
}
