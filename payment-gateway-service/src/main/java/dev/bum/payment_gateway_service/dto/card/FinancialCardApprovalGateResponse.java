package dev.bum.payment_gateway_service.dto.card;

public record FinancialCardApprovalGateResponse(
        // 원거래 조건을 재요청 때도 대조한다.
        FinancialCardApprovalGateRequest request,
        // true는 승인 없음이 확정됐고 앞으로의 승인도 영구 차단됐다는 의미다.
        boolean closed,
        // 승인이 먼저 커밋됐으면 그 최초 결과를 반환한다.
        FinancialCardApprovalResponse approval
) { }
