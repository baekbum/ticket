-- PostgreSQL: ticket-service의 결제·환불 테이블을 삭제하고 현재 엔티티 구조로 다시 생성한다.
-- 대상 테이블의 데이터는 삭제된다. 결제·입금·환불 및 관련 스케줄러의 쓰기를 중단하고 실행한다.
-- 예매·티켓·좌석·쿠폰·배송 테이블은 이 스크립트의 대상에 포함되지 않는다.
-- PG의 gateway_refunds가 사용하는 멱등 키와 충돌하지 않도록 환불 프로세스 ID 시퀀스는 유지한다.
BEGIN;

CREATE TEMP TABLE payment_reset_sequence_state (last_id BIGINT NOT NULL) ON COMMIT DROP;
DO $$
DECLARE
    sequence_name TEXT;
    sequence_last_id BIGINT := 0;
    process_max_id BIGINT := 0;
BEGIN
    IF to_regclass('payment_refund_processes') IS NOT NULL THEN
        SELECT COALESCE(MAX(payment_refund_process_id), 0) INTO process_max_id FROM payment_refund_processes;
        sequence_name := pg_get_serial_sequence('payment_refund_processes', 'payment_refund_process_id');
        IF sequence_name IS NOT NULL THEN
            EXECUTE format('SELECT last_value FROM %s', sequence_name) INTO sequence_last_id;
        END IF;
    END IF;
    INSERT INTO payment_reset_sequence_state VALUES (GREATEST(COALESCE(sequence_last_id, 0), process_max_id));
END $$;

-- 1. 자식 테이블부터 삭제한다. 이전 구조의 환불 티켓 이력 테이블도 정리한다.
DROP TABLE IF EXISTS payment_refund_history_tickets;
DROP TABLE IF EXISTS payment_refund_histories;
DROP TABLE IF EXISTS payment_refund_processes;
DROP TABLE IF EXISTS payment_refunds;
DROP TABLE IF EXISTS payment_refund_snapshot_tickets;
DROP TABLE IF EXISTS payment_refund_snapshots;
DROP TABLE IF EXISTS checkout_attempts;
DROP TABLE IF EXISTS payment_histories;
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS payment_snapshot_discounts;
DROP TABLE IF EXISTS payment_snapshot_tickets;
DROP TABLE IF EXISTS payment_snapshots;
DROP TABLE IF EXISTS payment_processes;

-- 2. 참조되는 테이블부터 생성한다.
-- payment_snapshots
CREATE TABLE payment_snapshots (
    payment_snapshot_id BIGSERIAL PRIMARY KEY,
    amount INTEGER NOT NULL,
    reservation_fee_amount INTEGER NOT NULL DEFAULT 0,
    delivery_fee_amount INTEGER NOT NULL DEFAULT 0,
    captured_at TIMESTAMP NOT NULL
);


-- payment_snapshot_tickets
CREATE TABLE payment_snapshot_tickets (
    payment_snapshot_id BIGINT NOT NULL REFERENCES payment_snapshots(payment_snapshot_id),
    item_order INTEGER NOT NULL,
    ticket_id BIGINT,
    ticket_price INTEGER NOT NULL,
    PRIMARY KEY(payment_snapshot_id,item_order)
);


-- payment_snapshot_discounts
CREATE TABLE payment_snapshot_discounts (
    payment_snapshot_id BIGINT NOT NULL REFERENCES payment_snapshots(payment_snapshot_id),
    item_order INTEGER NOT NULL,
    user_coupon_id BIGINT,
    discount_type VARCHAR(30),
    discount_name VARCHAR(100),
    coupon_discount_type VARCHAR(30),
    discount_value INTEGER,
    discount_amount INTEGER NOT NULL,
    PRIMARY KEY(payment_snapshot_id,item_order)
);


-- payment_processes
CREATE TABLE payment_processes (
    payment_process_id BIGSERIAL PRIMARY KEY,
    stage VARCHAR(40) NOT NULL,
    failure_reason VARCHAR(1000),
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_tried_at TIMESTAMP NOT NULL
);


-- payments
CREATE TABLE payments (
    payment_id BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    payment_no VARCHAR(60) NOT NULL,
    method VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    refunded_amount INTEGER NOT NULL DEFAULT 0,
    cancellation_fee_amount INTEGER NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(100) NOT NULL,
    payment_snapshot_id BIGINT NOT NULL UNIQUE REFERENCES payment_snapshots(payment_snapshot_id),
    payment_process_id BIGINT NOT NULL UNIQUE REFERENCES payment_processes(payment_process_id),
    card_transaction_id VARCHAR(80),
    card_company VARCHAR(30),
    card_number_masked VARCHAR(30),
    bank_name VARCHAR(50),
    account_number VARCHAR(50),
    depositor_name VARCHAR(50),
    requested_at TIMESTAMP NOT NULL,
    paid_at TIMESTAMP,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_payments_reservation_id UNIQUE (reservation_id),
    CONSTRAINT uk_payments_payment_no UNIQUE (payment_no),
    CONSTRAINT uk_payments_account_number UNIQUE (account_number),
    CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key)
);
CREATE INDEX idx_payment_reservation_id ON payments(reservation_id);
CREATE INDEX idx_payment_status_expires_at ON payments(status, expires_at);
CREATE INDEX idx_payments_idempotency_key_status ON payments(idempotency_key, status);

-- payment_histories
CREATE TABLE payment_histories (
    payment_history_id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payments(payment_id),
    event_type VARCHAR(40) NOT NULL,
    method VARCHAR(30) NOT NULL,
    before_status VARCHAR(30),
    after_status VARCHAR(30) NOT NULL,
    refunded_amount_after INTEGER NOT NULL,
    cancellation_fee_amount_after INTEGER NOT NULL,
    reason VARCHAR(1000),
    occurred_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_payment_histories_payment ON payment_histories(payment_id);

-- checkout_attempts
CREATE TABLE checkout_attempts (
    idempotency_key VARCHAR(100) PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    order_id VARCHAR(50) NOT NULL,
    event_id BIGINT NOT NULL,
    payment_no VARCHAR(60) NOT NULL,
    payment_id BIGINT,
    status VARCHAR(30) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_checkout_attempts_order_id UNIQUE (order_id),
    CONSTRAINT uk_checkout_attempts_payment_no UNIQUE (payment_no),
    CONSTRAINT uk_checkout_attempts_payment_id UNIQUE (payment_id)
);
CREATE INDEX idx_checkout_attempts_status_expires_at ON checkout_attempts(status, expires_at);

-- payment_refund_snapshots
CREATE TABLE payment_refund_snapshots (
    payment_refund_snapshot_id BIGSERIAL PRIMARY KEY,
    refund_amount INTEGER NOT NULL,
    cancellation_fee_amount INTEGER NOT NULL DEFAULT 0,
    full_cancellation BOOLEAN NOT NULL,
    ticket_amount INTEGER NOT NULL DEFAULT 0,
    discount_amount INTEGER NOT NULL DEFAULT 0,
    delivery_refund_amount INTEGER NOT NULL DEFAULT 0,
    refund_bank_company VARCHAR(30),
    refund_account_number_masked VARCHAR(50),
    refund_account_number_hash VARCHAR(64),
    refund_account_holder VARCHAR(50),
    captured_at TIMESTAMP NOT NULL
);


-- payment_refund_snapshot_tickets
CREATE TABLE payment_refund_snapshot_tickets (
    payment_refund_history_ticket_id BIGSERIAL PRIMARY KEY,
    refund_snapshot_id BIGINT NOT NULL REFERENCES payment_refund_snapshots(payment_refund_snapshot_id),
    ticket_id BIGINT NOT NULL,
    ticket_price INTEGER NOT NULL
);


-- payment_refunds
CREATE TABLE payment_refunds (
    payment_refund_id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    refund_snapshot_id BIGINT NOT NULL UNIQUE REFERENCES payment_refund_snapshots(payment_refund_snapshot_id),
    status VARCHAR(30) NOT NULL
);


-- payment_refund_processes
CREATE TABLE payment_refund_processes (
    payment_refund_process_id BIGSERIAL PRIMARY KEY,
    refund_id BIGINT NOT NULL UNIQUE REFERENCES payment_refunds(payment_refund_id),
    status VARCHAR(30) NOT NULL,
    failure_reason VARCHAR(1000),
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_tried_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_payment_refund_processes_status ON payment_refund_processes(status);

-- payment_refund_histories
CREATE TABLE payment_refund_histories (
    payment_refund_history_id BIGSERIAL PRIMARY KEY,
    payment_refund_process_id BIGINT NOT NULL REFERENCES payment_refund_processes(payment_refund_process_id),
    event_type VARCHAR(40) NOT NULL,
    before_status VARCHAR(30),
    after_status VARCHAR(30),
    failure_reason VARCHAR(1000),
    refunded_amount_after INTEGER,
    refundable_amount_after INTEGER,
    payment_status_after VARCHAR(30),
    success_key VARCHAR(100) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_refund_history_process ON payment_refund_histories(payment_refund_process_id);

-- 엔티티의 결제 참조 관계를 외래 키로 연결한다.
ALTER TABLE checkout_attempts ADD CONSTRAINT fk_checkout_attempts_payment
    FOREIGN KEY (payment_id) REFERENCES payments(payment_id);
ALTER TABLE payment_refunds ADD CONSTRAINT fk_payment_refunds_payment
    FOREIGN KEY (payment_id) REFERENCES payments(payment_id);

-- 3. 새 환불 요청에는 기존에 사용한 ID보다 큰 값을 발급한다.
SELECT setval(
    pg_get_serial_sequence('payment_refund_processes', 'payment_refund_process_id'),
    GREATEST(last_id, 1),
    last_id > 0
) FROM payment_reset_sequence_state;

COMMIT;
