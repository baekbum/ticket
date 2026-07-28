DROP TABLE IF EXISTS audit_log CASCADE;

CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    service_name VARCHAR(50) NOT NULL,

    actor_type VARCHAR(30) NOT NULL,
    actor_id VARCHAR(50),
    actor_name VARCHAR(100),

    action VARCHAR(100) NOT NULL,

    target_type VARCHAR(50),
    target_id VARCHAR(100),

    result VARCHAR(20) NOT NULL,
    reason VARCHAR(500),

    ip_address VARCHAR(45),
    user_agent VARCHAR(500),

    request_id VARCHAR(100),
    trace_id VARCHAR(100),

    before_data JSONB,
    after_data JSONB,
    metadata JSONB,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_occurred_at ON audit_log(occurred_at);
CREATE INDEX idx_audit_log_actor ON audit_log(actor_type, actor_id);
CREATE INDEX idx_audit_log_action ON audit_log(action);
CREATE INDEX idx_audit_log_target ON audit_log(target_type, target_id);
CREATE INDEX idx_audit_log_trace_id ON audit_log(trace_id);
