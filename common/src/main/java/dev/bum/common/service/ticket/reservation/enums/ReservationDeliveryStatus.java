package dev.bum.common.service.ticket.reservation.enums;

public enum ReservationDeliveryStatus {
    READY,      // 배송 정보 생성 후 출고 준비 전
    PREPARING,  // 운영자가 배송 출고를 준비 중
    SHIPPED,    // 택배사와 운송장 등록 후 발송 완료
    DELIVERED,  // 수령인에게 배송 완료
    RETURNED,   // 배송 실패 또는 고객 반송으로 회수 처리
    CANCELLED   // 예매 취소 또는 배송 전 취소로 배송 중단
}
