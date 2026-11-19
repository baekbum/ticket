package dev.bum.payment_gateway_service.exception;

public class FinancialApprovalRejectedException extends RuntimeException {

    public FinancialApprovalRejectedException() {

        super("카드 인증 정보와 사용 가능 상태를 확인해주세요.");
    }
}
