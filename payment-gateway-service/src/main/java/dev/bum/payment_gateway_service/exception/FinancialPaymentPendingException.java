package dev.bum.payment_gateway_service.exception;

import dev.bum.payment_gateway_service.dto.financial.FinancialCardPaymentPendingResponse;
import lombok.Getter;

@Getter
public class FinancialPaymentPendingException extends RuntimeException {

    // 정상 승인 결과와 구분해서 반환할 기존 202 응답.
    private final FinancialCardPaymentPendingResponse response;

    public FinancialPaymentPendingException(FinancialCardPaymentPendingResponse response) {

        super("금융 승인 결과를 확인하고 있습니다.");
        this.response = response;
    }
}
