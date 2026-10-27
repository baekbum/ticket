-- 환불 기록 테이블이 없는 PG DB에 적용한다. 기존 테이블 갱신 SQL은 docs/refund-idempotency.md를 참고한다.
CREATE TABLE IF NOT EXISTS gateway_refunds (
    payment_refund_process_id BIGINT PRIMARY KEY,
    payment_no VARCHAR(60) NOT NULL,
    method VARCHAR(30) NOT NULL,
    refund_amount NUMERIC(15, 2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    failure_reason VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    transaction_id VARCHAR(100),
    refund_bank_company VARCHAR(30),
    refund_account_number VARCHAR(100),
    refund_account_holder VARCHAR(100),
    card_status VARCHAR(40),
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_gateway_refunds_status CHECK (status IN ('REQUESTED', 'SUCCEEDED', 'FAILED', 'CLOSED')),
    CONSTRAINT chk_gateway_refunds_process_id CHECK (payment_refund_process_id > 0),
    CONSTRAINT chk_gateway_refunds_amount CHECK (refund_amount > 0)
);
CREATE INDEX IF NOT EXISTS idx_gateway_refunds_payment_no ON gateway_refunds(payment_no);
