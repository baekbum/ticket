DROP TABLE IF EXISTS audit_log CASCADE;
DROP TABLE IF EXISTS auth CASCADE;

-- ==========================================
-- 1. 권한 테이블
-- ==========================================
CREATE TABLE auth (
    id BIGINT PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER'
);

CREATE INDEX idx_auth_user_id ON auth(user_id);

-- ==========================================
-- 2. 감사 로그 테이블
-- ==========================================
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
