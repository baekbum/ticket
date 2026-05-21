package dev.bum.ticket_service.enums;

public enum TicketStatus {
    READY_TO_PAY,       // 결제 대기 (카드/간편결제 창 호출 상태 - 보통 5~10분 내 결정)
    AWAITING_DEPOSIT,   // 입금 대기 (무통장 가상계좌 발급 상태 - 보통 익일 자정까지 유효)
    PAYMENT_COMPLETED,  // 결제 완료 (카드 승인 완료 또는 무통장 입금 확인 완료)
    CANCELLED           // 취소됨 (미입금 만료 또는 유저 취소)
}
