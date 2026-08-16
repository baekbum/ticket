-- ==========================================
-- Dummy cards 테이블
-- ==========================================
CREATE TABLE dummy_cards (
    dummy_card_id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    card_company VARCHAR(30) NOT NULL,
    card_number_hash VARCHAR(128) NOT NULL,
    card_number_last4 VARCHAR(4) NOT NULL,
    cvc_hash VARCHAR(128) NOT NULL,
    card_password_hash VARCHAR(128) NOT NULL,
    customer_name VARCHAR(50) NOT NULL,
    issued_at DATE NOT NULL,
    expires_at DATE NOT NULL,
    current_month_used_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
    limit_amount NUMERIC(15, 2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_dummy_cards_card_number_hash UNIQUE (card_number_hash),
    CONSTRAINT chk_dummy_cards_current_month_used_amount CHECK (current_month_used_amount >= 0),
    CONSTRAINT chk_dummy_cards_limit_amount CHECK (limit_amount >= 0),
    CONSTRAINT chk_dummy_cards_card_number_last4 CHECK (card_number_last4 ~ '^[0-9]{4}$'),
    CONSTRAINT chk_dummy_cards_expires_at CHECK (expires_at >= issued_at)
);

CREATE INDEX idx_dummy_cards_user_id ON dummy_cards(user_id);
CREATE INDEX idx_dummy_cards_customer_name ON dummy_cards(customer_name);
CREATE INDEX idx_dummy_cards_card_company ON dummy_cards(card_company);

-- ==========================================
-- Dummy card payment histories 테이블
-- ==========================================
CREATE TABLE dummy_card_payment_histories (
    history_id BIGSERIAL PRIMARY KEY,
    dummy_card_id BIGINT NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    payment_no VARCHAR(60) NOT NULL,
    card_company VARCHAR(30) NOT NULL,
    card_number_last4 VARCHAR(4) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    status VARCHAR(40) NOT NULL,
    failure_reason VARCHAR(500),
    approved_at TIMESTAMP NOT NULL,
    ticket_completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_dummy_card_payment_histories_dummy_card_id FOREIGN KEY (dummy_card_id) REFERENCES dummy_cards(dummy_card_id),
    CONSTRAINT uk_dummy_card_payment_histories_payment_no UNIQUE (payment_no),
    CONSTRAINT chk_dummy_card_payment_histories_amount CHECK (amount > 0),
    CONSTRAINT chk_dummy_card_payment_histories_card_number_last4 CHECK (card_number_last4 ~ '^[0-9]{4}$')
);

CREATE INDEX idx_dummy_card_payment_histories_user_id ON dummy_card_payment_histories(user_id);
CREATE INDEX idx_dummy_card_payment_histories_payment_no ON dummy_card_payment_histories(payment_no);
CREATE INDEX idx_dummy_card_payment_histories_status ON dummy_card_payment_histories(status);
