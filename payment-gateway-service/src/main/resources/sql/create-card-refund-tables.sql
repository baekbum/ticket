-- 기존 은행 환불·카드 원승인 데이터는 유지하고 카드 개별 환불 기록만 추가한다.
BEGIN;

CREATE TABLE gateway_card_refunds (
    refund_id BIGSERIAL PRIMARY KEY,
    pg_payment_id VARCHAR(44) NOT NULL,
    refund_request_id VARCHAR(60) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    completed_at TIMESTAMP(6),

    CONSTRAINT uk_gateway_card_refund_request UNIQUE (pg_payment_id, refund_request_id),
    CONSTRAINT fk_gateway_card_refund_payment FOREIGN KEY (pg_payment_id)
        REFERENCES gateway_payments(pg_payment_id),
    CONSTRAINT chk_gateway_card_refund_amount CHECK (amount > 0),
    CONSTRAINT chk_gateway_card_refund_status CHECK (status IN ('REQUESTED', 'SUCCEEDED', 'WITHDRAWN')),
    CONSTRAINT chk_gateway_card_refund_completion CHECK (
        (status = 'REQUESTED' AND completed_at IS NULL)
        OR (status <> 'REQUESTED' AND completed_at IS NOT NULL)
    )
);

CREATE TABLE card_payment_refunds (
    refund_id BIGSERIAL PRIMARY KEY,
    pg_payment_id VARCHAR(44) NOT NULL,
    refund_request_id VARCHAR(60) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    completed_at TIMESTAMP(6) NOT NULL,

    CONSTRAINT uk_card_payment_refund_request UNIQUE (pg_payment_id, refund_request_id),
    CONSTRAINT fk_card_payment_refund_approval FOREIGN KEY (pg_payment_id)
        REFERENCES dummy_card_payment_histories(pg_payment_id),
    CONSTRAINT chk_card_payment_refund_amount CHECK (amount > 0),
    CONSTRAINT chk_card_payment_refund_status CHECK (status IN ('SUCCEEDED', 'WITHDRAWN'))
);

COMMIT;
