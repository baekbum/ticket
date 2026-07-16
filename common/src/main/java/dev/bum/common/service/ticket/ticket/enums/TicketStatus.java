package dev.bum.common.service.ticket.ticket.enums;

public enum TicketStatus {
    PENDING_PAYMENT,    // 예약 생성 후 결제 대기
    PAID,               // 결제 완료
    CANCELLED,          // 사용자 취소 또는 예약 취소
    EXPIRED             // 결제 제한 시간 만료
}
