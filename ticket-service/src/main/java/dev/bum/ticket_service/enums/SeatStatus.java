package dev.bum.ticket_service.enums;

public enum SeatStatus {
    AVAILABLE,  // 예매 가능
    RESERVED,   // 예매됨
    LOCKED      // 결제 진행 중 (선점), 후에 결제 기능이 추가될 때 사용
}
