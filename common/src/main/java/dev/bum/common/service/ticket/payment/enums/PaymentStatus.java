package dev.bum.common.service.ticket.payment.enums;

public enum PaymentStatus {
    READY,              // 결제 요청 생성 후 승인 또는 입금 대기 전
    WAITING_DEPOSIT,    // 무통장 입금 계좌 발급 후 입금 대기
    PAID,               // 결제 승인 또는 입금 확인 완료
    FAILED,             // 결제 승인 실패 또는 결제 처리 실패
    CANCELLED,          // 결제 완료 전 결제 요청 취소
    EXPIRED,            // 결제 가능 시간이 지나 만료
    REFUNDED            // 결제 완료 후 환불 처리 완료
}
