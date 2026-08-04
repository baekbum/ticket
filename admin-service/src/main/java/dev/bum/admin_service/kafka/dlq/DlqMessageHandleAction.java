package dev.bum.admin_service.kafka.dlq;

public enum DlqMessageHandleAction {
    /**
     * DLT 메시지를 원본 topic으로 다시 발행하는 처리.
     */
    REPLAY,

    /**
     * DLT 메시지 payload를 운영자가 보정한 뒤 원본 topic으로 다시 발행하는 처리.
     */
    MODIFIED_REPLAY,

    /**
     * DLT 메시지를 더 이상 재처리하지 않기로 결정하는 폐기 처리.
     */
    DISCARD
}
