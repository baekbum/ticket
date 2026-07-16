package dev.bum.common.service.ticket.reservation.enums;

public enum ReservationStatus {
    PENDING_PAYMENT,    // 예약 생성 후 결제 대기
    PAID,               // 결제 완료로 예매 확정
    PARTIALLY_CANCELLED, // 일부 티켓만 취소됨
    CANCELLED,          // 전체 취소
    EXPIRED             // 결제 제한 시간 만료
}
