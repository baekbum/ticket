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
    dummy_card_id BIGINT,
    user_id VARCHAR(50) NOT NULL,
    payment_no VARCHAR(60) NOT NULL,
    transaction_id VARCHAR(80),
    card_company VARCHAR(30) NOT NULL,
    card_number_masked VARCHAR(30) NOT NULL,
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
    CONSTRAINT chk_dummy_card_payment_histories_card_number_masked CHECK (card_number_masked ~ '^[0-9*]{4}-[0-9*]{4}-[0-9*]{4}-[0-9*]{4}$')
);

CREATE INDEX idx_dummy_card_payment_histories_user_id ON dummy_card_payment_histories(user_id);
CREATE INDEX idx_dummy_card_payment_histories_payment_no ON dummy_card_payment_histories(payment_no);
CREATE INDEX idx_dummy_card_payment_histories_status ON dummy_card_payment_histories(status);

-- ==========================================
-- Dummy virtual accounts 테이블
-- ==========================================
CREATE TABLE dummy_virtual_accounts (
    virtual_account_id BIGSERIAL PRIMARY KEY,
    payment_no VARCHAR(60) NOT NULL,
    bank_company VARCHAR(30) NOT NULL,
    bank_name VARCHAR(50) NOT NULL,
    account_number VARCHAR(30) NOT NULL,
    depositor_name VARCHAR(50),
    amount NUMERIC(15, 2) NOT NULL,
    status VARCHAR(40) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    deposited_at TIMESTAMP,
    ticket_completed_at TIMESTAMP,
    failure_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_dummy_virtual_accounts_payment_no UNIQUE (payment_no),
    CONSTRAINT uk_dummy_virtual_accounts_account_number UNIQUE (account_number),
    CONSTRAINT chk_dummy_virtual_accounts_amount CHECK (amount > 0)
);

CREATE INDEX idx_dummy_virtual_accounts_payment_no ON dummy_virtual_accounts(payment_no);
CREATE INDEX idx_dummy_virtual_accounts_account_number ON dummy_virtual_accounts(account_number);
CREATE INDEX idx_dummy_virtual_accounts_status ON dummy_virtual_accounts(status);

-- ==========================================
-- Dummy virtual account payment histories 테이블
-- ==========================================
CREATE TABLE dummy_virtual_account_payment_histories (
    history_id BIGSERIAL PRIMARY KEY,
    virtual_account_id BIGINT NOT NULL,
    payment_no VARCHAR(60) NOT NULL,
    history_type VARCHAR(40) NOT NULL,
    message VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_dummy_virtual_account_histories_virtual_account_id FOREIGN KEY (virtual_account_id) REFERENCES dummy_virtual_accounts(virtual_account_id)
);

CREATE INDEX idx_dummy_virtual_account_histories_payment_no ON dummy_virtual_account_payment_histories(payment_no);
CREATE INDEX idx_dummy_virtual_account_histories_history_type ON dummy_virtual_account_payment_histories(history_type);

-- ==========================================
-- Virtual account outbox events 테이블
-- ==========================================
CREATE TABLE virtual_account_outbox_events (
    outbox_id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(60) NOT NULL,
    status VARCHAR(30) NOT NULL,
    payment_no VARCHAR(60) NOT NULL,
    bank_company VARCHAR(30) NOT NULL,
    bank_name VARCHAR(50) NOT NULL,
    account_number VARCHAR(30) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error_message VARCHAR(500),
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_virtual_account_outbox_amount CHECK (amount > 0),
    CONSTRAINT chk_virtual_account_outbox_retry_count CHECK (retry_count >= 0)
);

CREATE INDEX idx_virtual_account_outbox_type_status_id ON virtual_account_outbox_events(event_type, status, outbox_id);
CREATE INDEX idx_virtual_account_outbox_payment_no ON virtual_account_outbox_events(payment_no);
