package dev.bum.ticket_service.service.payment.refund;

/** 저장된 환불 상태를 확인한 뒤 이번 호출에서 수행할 작업이다. */
public enum RefundAction {
    REQUEST_GATEWAY,          // PG 환불을 실행하고 로컬에 반영한다.
    APPLY_LOCAL,              // PG를 재호출하지 않고 이번 요청을 로컬에 반영한다.
    RECOVER_PREVIOUS_REQUEST  // 기존 요청만 복구하고 변경 요청은 실행하지 않는다.
}
