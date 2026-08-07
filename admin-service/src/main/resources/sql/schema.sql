-- ==========================================
-- DLQ 메세지 처리 이력
-- ==========================================
CREATE TABLE IF NOT EXISTS dlq_message_handle_histories (
    id BIGSERIAL PRIMARY KEY,
    dlt_topic VARCHAR(150) NOT NULL,
    partition_no INTEGER NOT NULL,
    message_offset BIGINT NOT NULL,
    message_key VARCHAR(500),
    target_topic VARCHAR(150),
    action VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    operator VARCHAR(100) NOT NULL,
    reason VARCHAR(500),
    error_message TEXT,
    original_payload TEXT,
    modified_payload TEXT,
    payload_modified BOOLEAN NOT NULL DEFAULT FALSE,
    handled_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dlq_handle_history_message
    ON dlq_message_handle_histories(dlt_topic, partition_no, message_offset);

CREATE INDEX IF NOT EXISTS idx_dlq_handle_history_status_handled_at
    ON dlq_message_handle_histories(status, handled_at);

CREATE INDEX IF NOT EXISTS idx_dlq_handle_history_action_handled_at
    ON dlq_message_handle_histories(action, handled_at);
