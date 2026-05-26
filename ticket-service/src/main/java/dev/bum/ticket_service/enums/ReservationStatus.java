package dev.bum.ticket_service.enums;

public enum ReservationStatus {
    CONFIRMED,          // 예매 완료 (속한 모든 티켓이 CONFIRMED)
    PARTIALLY_CANCELLED, // 부분 취소 (일부 티켓만 취소됨)
    CANCELLED           // 전체 취소 (속한 모든 티켓이 CANCELLED)
}
