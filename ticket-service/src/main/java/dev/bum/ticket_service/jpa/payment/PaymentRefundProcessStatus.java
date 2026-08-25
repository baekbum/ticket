package dev.bum.ticket_service.jpa.payment;

public enum PaymentRefundProcessStatus {
    // 환불 요청이 생성되었고 gateway 호출 전인 상태.
    REQUESTED,

    // gateway 환불은 성공했고 로컬 DB 상태 변경이 남은 상태.
    GATEWAY_SUCCEEDED,

    // gateway 환불 호출이 실패해 로컬 DB 상태를 변경하지 않은 상태.
    GATEWAY_FAILED,

    // gateway 환불과 로컬 DB 상태 변경이 모두 완료된 상태.
    LOCAL_SUCCEEDED,

    // gateway 환불은 성공했지만 로컬 DB 상태 변경 또는 커밋이 실패한 상태.
    LOCAL_FAILED
}
