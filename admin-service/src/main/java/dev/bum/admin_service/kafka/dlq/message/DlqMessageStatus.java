package dev.bum.admin_service.kafka.dlq.message;

public enum DlqMessageStatus {
    PENDING, REPLAYING, REPLAYED, MODIFIED_REPLAYED, DISCARDED, FAILED, UNKNOWN
}
