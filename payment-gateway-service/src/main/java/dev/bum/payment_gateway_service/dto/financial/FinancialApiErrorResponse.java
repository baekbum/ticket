package dev.bum.payment_gateway_service.dto.financial;

public record FinancialApiErrorResponse(
        // 금융 API가 정의한 업무/저장소 오류 코드. 없는 응답도 허용한다.
        String code,

        // 기존 계약의 안내 문구. HTTP 예외의 문자열 표현에는 넣지 않는다.
        String message,

        // 프레임워크가 생성한 오류 응답의 발생 시각.
        String timestamp,

        // 프레임워크 오류 본문의 숫자 HTTP 상태. 승인 대기의 PENDING 문자열과 분리한다.
        Integer status,

        // 프레임워크 오류의 상태 설명.
        String error,

        // 기존 프레임워크 응답에 포함된 요청 경로.
        String path
) {
}
