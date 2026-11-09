package dev.bum.payment_gateway_service.dto.gateway;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.bum.payment_gateway_service.dto.financial.FinancialApiErrorResponse;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GatewayApiErrorResponse(
        // 기존 PG 또는 금융 오류 코드.
        String code,

        // 기존 오류 계약의 안내 문구.
        String message,

        // 금융 프레임워크 오류에 포함됐던 시각. 없는 필드는 응답에 추가하지 않는다.
        String timestamp,

        // 프레임워크 오류의 숫자 상태. 업무의 PENDING 상태와 다르다.
        Integer status,

        // 프레임워크 오류의 상태 설명.
        String error,

        // 기존 프레임워크 오류의 경로.
        String path
) {

    // 금융 오류를 HTTP 상태와 함께 PG 경계에서 명시적으로 변환한다.
    public static GatewayApiErrorResponse from(FinancialApiErrorResponse response) {

        return response == null ? null : new GatewayApiErrorResponse(response.code(), response.message(),
                response.timestamp(), response.status(), response.error(), response.path());
    }
}
