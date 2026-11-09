package dev.bum.payment_gateway_service.exception;

import dev.bum.payment_gateway_service.dto.financial.FinancialApiErrorResponse;
import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class FinancialApiException extends RuntimeException {

    // 금융 API가 반환한 HTTP 상태. 요청을 다시 실행하지 않는다.
    private final HttpStatusCode statusCode;
    // 정의된 오류 필드만 보존한다. 빈 인증 오류는 null이다.
    private final FinancialApiErrorResponse response;

    public FinancialApiException(HttpStatusCode statusCode, FinancialApiErrorResponse response) {

        super("금융 API가 오류 상태를 반환했습니다.");
        this.statusCode = statusCode;
        this.response = response;
    }
}
