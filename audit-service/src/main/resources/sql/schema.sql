DROP TABLE IF EXISTS audit_log CASCADE;
DROP TABLE IF EXISTS login_log CASCADE;

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

CREATE TABLE login_log (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL,
    auth_id BIGINT,
    login_id VARCHAR(100) NOT NULL,
    result VARCHAR(20) NOT NULL,
    auth_method VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(100),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    session_id VARCHAR(100),
    request_id VARCHAR(100),
    trace_id VARCHAR(100),
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_login_log_event_id UNIQUE (event_id)
);

CREATE INDEX idx_login_log_occurred_at ON login_log(occurred_at);
CREATE INDEX idx_login_log_auth_id ON login_log(auth_id);
CREATE INDEX idx_login_log_login_id ON login_log(login_id);
CREATE INDEX idx_login_log_result_occurred_at ON login_log(result, occurred_at);
CREATE INDEX idx_login_log_ip_address_occurred_at ON login_log(ip_address, occurred_at);
