DROP TABLE IF EXISTS audit_log CASCADE;
DROP TABLE IF EXISTS user_addresses CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- ==========================================
-- 1. User 테이블
-- ==========================================
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER',
    grade VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    name VARCHAR(20) NOT NULL,
    phone_number VARCHAR(15) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    birth_date DATE,
    address TEXT,
    is_blacklisted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_user_id ON users(user_id);
CREATE INDEX idx_users_email ON users(email);

-- ==========================================
-- 2. UserAddress 테이블
-- ==========================================
CREATE TABLE user_addresses (
    address_id BIGSERIAL PRIMARY KEY,
    user_pk BIGINT NOT NULL,
    alias VARCHAR(50),
    recipient_name VARCHAR(30) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    zip_code VARCHAR(10) NOT NULL,
    address VARCHAR(255) NOT NULL,
    detail_address VARCHAR(255),
    default_address BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_addresses_user
        FOREIGN KEY (user_pk)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_user_addresses_user_pk ON user_addresses(user_pk);
CREATE INDEX idx_user_addresses_status ON user_addresses(status);
CREATE INDEX idx_user_addresses_default ON user_addresses(user_pk, default_address);
CREATE UNIQUE INDEX uk_user_addresses_default_active
    ON user_addresses(user_pk)
    WHERE default_address = TRUE AND status = 'ACTIVE';

-- ==========================================
-- 3. 감사 로그 테이블
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
