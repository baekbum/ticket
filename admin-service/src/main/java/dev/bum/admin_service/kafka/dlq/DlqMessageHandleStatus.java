package dev.bum.admin_service.kafka.dlq;

public enum DlqMessageHandleStatus {
    /**
     * replay 또는 discard 처리가 정상 완료된 상태.
     */
    SUCCESS,

    /**
     * replay 또는 discard 처리 중 오류가 발생한 상태.
     */
    FAILED
}
