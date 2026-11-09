package dev.bum.payment_gateway_service.dto.gateway;

import dev.bum.payment_gateway_service.dto.card.FinancialCardApproveRequest;

public record GatewayCardApprovalPreparation(
        // PG 트랜잭션 밖에서 전송할 카드사 입력.
        FinancialCardApproveRequest financialRequest,

        // 성공한 승인 작업은 금융 재호출 없이 재응답한다.
        GatewayCardApprovalResponse replay,

        // 새로운 금융 실행이 가능한 가맹점 상태.
        boolean activeMerchant,

        // 신규 승인 전에 지원 여부를 검사할 통보 DTO/응답 계약의 버전 코드.
        // 공통 PAYMENT_NOTIFICATION_V1과 기존 카드 V1을 검사하며 이 값으로 DTO나 구현체를 자동 선택하지 않는다.
        String notificationContract,

        // 가맹점 ID와 함께 DB의 수신 API URL·암호화 인증값을 찾는 대상 키. 통보 DTO 형식과는 별개다.
        String notificationTargetKey
) {

    // 중첩된 금융 요청의 비밀정보도 문자열 표현에 노출하지 않는다.
    @Override
    public String toString() {

        return "GatewayCardApprovalPreparation[REDACTED]";
    }
}
