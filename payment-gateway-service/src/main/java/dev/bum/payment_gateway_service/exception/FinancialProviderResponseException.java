package dev.bum.payment_gateway_service.exception;

public class FinancialProviderResponseException extends RuntimeException {

    public FinancialProviderResponseException() {

        super("금융기관의 처리 결과를 확인할 수 없습니다.");
    }

    public FinancialProviderResponseException(Throwable cause) {

        super("금융기관의 처리 결과를 확인할 수 없습니다.", cause);
    }
}
