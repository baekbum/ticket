-- 기존 ticket DB에 적용하는 비파괴 추가 스키마. schema.sql 전체를 실행하지 않는다.
CREATE TABLE IF NOT EXISTS virtual_account_cancel_outbox (
    outbox_id BIGSERIAL PRIMARY KEY,
    payment_no VARCHAR(60) NOT NULL,
    bank_name VARCHAR(50) NOT NULL,
    account_number VARCHAR(50) NOT NULL,
    amount INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error_type VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP,
    CONSTRAINT uk_va_cancel_outbox_payment_no UNIQUE (payment_no),
    CONSTRAINT chk_va_cancel_outbox_status CHECK (status IN ('PENDING', 'DELIVERED')),
    CONSTRAINT chk_va_cancel_outbox_retry_count CHECK (retry_count >= 0),
    CONSTRAINT chk_va_cancel_outbox_amount CHECK (amount >= 0)
);
CREATE INDEX IF NOT EXISTS idx_va_cancel_outbox_ready
    ON virtual_account_cancel_outbox(status, next_attempt_at, outbox_id);
