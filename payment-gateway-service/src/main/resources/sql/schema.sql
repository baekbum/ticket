-- PG 가맹점·거래·처리 이력
CREATE TABLE merchants (
    -- 숫자 PK 자체를 가맹점 코드로 사용한다. 가맹점명은 중복을 허용한다.
    merchant_id BIGSERIAL PRIMARY KEY,
    merchant_name VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SUSPENDED',
    credential_hash VARCHAR(64),
    notification_contract VARCHAR(40) NOT NULL,
    notification_target_key VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_merchants_credential_hash UNIQUE (credential_hash),
    CONSTRAINT chk_merchants_id CHECK (merchant_id > 0),
    CONSTRAINT chk_merchants_name CHECK (btrim(merchant_name) <> ''),
    CONSTRAINT chk_merchants_credential_hash CHECK (credential_hash IS NULL OR credential_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_merchants_notification_config CHECK (
        btrim(notification_contract) <> '' AND btrim(notification_target_key) <> ''
    ),
    CONSTRAINT chk_merchants_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

-- 가맹점별 통보 URL·발신 인증값. 비밀값은 외부 키로 암호화한 뒤 등록한다.
CREATE TABLE merchant_notification_endpoints (
    endpoint_id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    target_key VARCHAR(100) NOT NULL,
    notification_url VARCHAR(2048) NOT NULL,
    service_token_ciphertext TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_merchant_notification_endpoints_merchant FOREIGN KEY (merchant_id)
        REFERENCES merchants(merchant_id),
    CONSTRAINT uk_merchant_notification_endpoints_target UNIQUE (merchant_id, target_key),
    CONSTRAINT chk_merchant_notification_endpoints_key CHECK (btrim(target_key) <> '' AND target_key = btrim(target_key)),
    CONSTRAINT chk_merchant_notification_endpoints_url CHECK (btrim(notification_url) <> ''),
    CONSTRAINT chk_merchant_notification_endpoints_token CHECK (service_token_ciphertext LIKE 'v1:%')
);

CREATE TABLE gateway_payments (
    pg_payment_id VARCHAR(44) PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    merchant_payment_no VARCHAR(60) NOT NULL,
    buyer_reference VARCHAR(50) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    card_company VARCHAR(30),
    bank_company VARCHAR(30),
    amount NUMERIC(15, 2) NOT NULL,
    refunded_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'READY',
    creation_request_hash CHAR(64) NOT NULL,
    method_revision INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMP(6) NOT NULL,
    paid_at TIMESTAMP(6),
    cancelled_at TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_gateway_payments_merchant FOREIGN KEY (merchant_id)
        REFERENCES merchants(merchant_id),
    CONSTRAINT uk_gateway_payments_merchant_no UNIQUE (merchant_id, merchant_payment_no),
    -- 하위 테이블의 merchant 스냅샷과 거래 소유 관계를 검증할 복합 FK의 참조 대상.
    CONSTRAINT uk_gateway_payments_id_merchant UNIQUE (pg_payment_id, merchant_id),
    CONSTRAINT chk_gateway_payments_no CHECK (btrim(merchant_payment_no) <> ''),
    CONSTRAINT chk_gateway_payments_buyer CHECK (btrim(buyer_reference) <> ''),
    CONSTRAINT chk_gateway_payments_method CHECK (payment_method IN ('CREDIT_CARD', 'BANK_TRANSFER')),
    CONSTRAINT chk_gateway_payments_company CHECK (
        (payment_method = 'CREDIT_CARD' AND bank_company IS NULL)
        OR (payment_method = 'BANK_TRANSFER' AND card_company IS NULL AND bank_company IS NOT NULL)
    ),
    CONSTRAINT chk_gateway_payments_amount CHECK (amount > 0),
    CONSTRAINT chk_gateway_payments_refunded CHECK (refunded_amount >= 0 AND refunded_amount <= amount),
    CONSTRAINT chk_gateway_payments_status CHECK (status IN (
        'READY', 'WAITING_DEPOSIT', 'PAID', 'PARTIALLY_REFUNDED',
        'REFUNDED', 'CANCELLED', 'EXPIRED', 'FAILED'
    )),
    CONSTRAINT chk_gateway_payments_waiting_method CHECK (
        status <> 'WAITING_DEPOSIT' OR payment_method = 'BANK_TRANSFER'
    ),
    CONSTRAINT chk_gateway_payments_refund_state CHECK (
        (status <> 'PARTIALLY_REFUNDED' OR (refunded_amount > 0 AND refunded_amount < amount))
        AND (status <> 'REFUNDED' OR refunded_amount = amount)
    ),
    CONSTRAINT chk_gateway_payments_request_hash CHECK (creation_request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_gateway_payments_method_revision CHECK (method_revision >= 0),
    CONSTRAINT chk_gateway_payments_version CHECK (version >= 0)
);

CREATE INDEX idx_gateway_payments_merchant_buyer
    ON gateway_payments(merchant_id, buyer_reference, created_at, pg_payment_id);
CREATE INDEX idx_gateway_payments_expiration
    ON gateway_payments(status, expires_at, pg_payment_id);

CREATE TABLE gateway_payment_histories (
    history_id BIGSERIAL PRIMARY KEY,
    pg_payment_id VARCHAR(44) NOT NULL,
    operation_type VARCHAR(40) NOT NULL,
    operation_key VARCHAR(100) NOT NULL,
    before_status VARCHAR(30),
    after_status VARCHAR(30) NOT NULL,
    amount NUMERIC(15, 2),
    request_hash CHAR(64),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_gateway_payment_histories_payment FOREIGN KEY (pg_payment_id)
        REFERENCES gateway_payments(pg_payment_id),
    CONSTRAINT uk_gateway_payment_histories_operation
        UNIQUE (pg_payment_id, operation_type, operation_key),
    CONSTRAINT chk_gateway_payment_histories_operation CHECK (operation_type IN (
        'REGISTER', 'APPROVE', 'ISSUE_ACCOUNT', 'DEPOSIT', 'CANCEL',
        'CANCEL_APPROVAL', 'EXPIRE', 'REFUND', 'CHANGE_METHOD', 'MIGRATE'
    )),
    CONSTRAINT chk_gateway_payment_histories_key CHECK (btrim(operation_key) <> ''),
    CONSTRAINT chk_gateway_payment_histories_amount CHECK (amount IS NULL OR amount >= 0),
    CONSTRAINT chk_gateway_payment_histories_request_hash
        CHECK (request_hash IS NULL OR request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_gateway_payment_histories_details CHECK (jsonb_typeof(details) = 'object'),
    CONSTRAINT chk_gateway_payment_histories_before_status CHECK (
        before_status IS NULL OR before_status IN (
            'READY', 'WAITING_DEPOSIT', 'PAID', 'PARTIALLY_REFUNDED',
            'REFUNDED', 'CANCELLED', 'EXPIRED', 'FAILED'
        )
    ),
    CONSTRAINT chk_gateway_payment_histories_after_status CHECK (after_status IN (
        'READY', 'WAITING_DEPOSIT', 'PAID', 'PARTIALLY_REFUNDED',
        'REFUNDED', 'CANCELLED', 'EXPIRED', 'FAILED'
    ))
);

CREATE INDEX idx_gateway_payment_histories_payment_time
    ON gateway_payment_histories(pg_payment_id, occurred_at, history_id);

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
    -- 기존 API 전환과 검증된 데이터 이관이 끝난 뒤 NOT NULL로 강화한다.
    pg_payment_id VARCHAR(44),
    user_id VARCHAR(50) NOT NULL,
    payment_no VARCHAR(60) NOT NULL,
    transaction_id VARCHAR(80),
    card_company VARCHAR(30) NOT NULL,
    card_number_masked VARCHAR(30) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    refunded_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
    status VARCHAR(40) NOT NULL,
    failure_reason VARCHAR(500),
    approved_at TIMESTAMP NOT NULL,
    ticket_completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_dummy_card_payment_histories_dummy_card_id FOREIGN KEY (dummy_card_id)
        REFERENCES dummy_cards(dummy_card_id),
    CONSTRAINT fk_dummy_card_payment_histories_pg_payment FOREIGN KEY (pg_payment_id)
        REFERENCES gateway_payments(pg_payment_id),
    CONSTRAINT uk_dummy_card_payment_histories_pg_payment UNIQUE (pg_payment_id),
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
    -- 기존 API 전환과 검증된 데이터 이관이 끝난 뒤 NOT NULL로 강화한다.
    pg_payment_id VARCHAR(44),
    payment_no VARCHAR(60) NOT NULL,
    bank_company VARCHAR(30) NOT NULL,
    bank_name VARCHAR(50) NOT NULL,
    account_number VARCHAR(30) NOT NULL,
    depositor_name VARCHAR(50),
    amount NUMERIC(15, 2) NOT NULL,
    refunded_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
    status VARCHAR(40) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    deposited_at TIMESTAMP,
    failure_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_dummy_virtual_accounts_refund_balance CHECK (refunded_amount >= 0 AND refunded_amount <= amount),
    CONSTRAINT fk_dummy_virtual_accounts_pg_payment FOREIGN KEY (pg_payment_id)
        REFERENCES gateway_payments(pg_payment_id),
    CONSTRAINT uk_dummy_virtual_accounts_pg_payment UNIQUE (pg_payment_id),
    CONSTRAINT uk_dummy_virtual_accounts_payment_no UNIQUE (payment_no),
    CONSTRAINT uk_dummy_virtual_accounts_account_number UNIQUE (account_number),
    CONSTRAINT chk_dummy_virtual_accounts_amount CHECK (amount > 0)
);

CREATE INDEX idx_dummy_virtual_accounts_payment_no ON dummy_virtual_accounts(payment_no);
CREATE INDEX idx_dummy_virtual_accounts_account_number ON dummy_virtual_accounts(account_number);
CREATE INDEX idx_dummy_virtual_accounts_status ON dummy_virtual_accounts(status);
CREATE INDEX idx_dummy_virtual_accounts_expiration ON dummy_virtual_accounts(status, expires_at, pg_payment_id);

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

    CONSTRAINT fk_dummy_virtual_account_histories_virtual_account_id FOREIGN KEY (virtual_account_id)
        REFERENCES dummy_virtual_accounts(virtual_account_id)
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

-- 입금 사실과 함께 커밋하고 ticket HTTP 성공 응답까지 재전달하는 Outbox.
CREATE TABLE virtual_account_deposit_outbox (
    outbox_id BIGSERIAL PRIMARY KEY,
    payment_no VARCHAR(60) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    bank_company VARCHAR(30) NOT NULL,
    bank_name VARCHAR(50) NOT NULL,
    account_number VARCHAR(30) NOT NULL,
    depositor_name VARCHAR(50) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    deposited_at TIMESTAMP NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error_type VARCHAR(100),
    delivered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_va_deposit_outbox_payment_no UNIQUE (payment_no),
    CONSTRAINT chk_va_deposit_outbox_status CHECK (status IN ('PENDING', 'DELIVERED')),
    CONSTRAINT chk_va_deposit_outbox_amount CHECK (amount > 0),
    CONSTRAINT chk_va_deposit_outbox_retry_count CHECK (retry_count >= 0)
);

CREATE INDEX idx_va_deposit_outbox_ready ON virtual_account_deposit_outbox(status, next_attempt_at, outbox_id);

-- 환불 프로세스별 최초 요청을 보존하고 성공·실패·미실행 종료 상태를 기록한다.
CREATE TABLE gateway_refunds (
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

CREATE INDEX idx_gateway_refunds_payment_no ON gateway_refunds(payment_no);

-- 기존 DB에 한 번만 적용한다. 기존 테이블/행은 변경하거나 삭제하지 않는다.

CREATE TABLE gateway_card_notification_outbox (
    notification_id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL,
    pg_payment_id VARCHAR(44) NOT NULL,
    merchant_id BIGINT NOT NULL,
    merchant_payment_no VARCHAR(60) NOT NULL,
    buyer_reference VARCHAR(50) NOT NULL,
    notification_contract VARCHAR(40) NOT NULL,
    notification_target_key VARCHAR(100) NOT NULL,
    transaction_id VARCHAR(80) NOT NULL,
    card_company VARCHAR(30) NOT NULL,
    masked_card_number VARCHAR(30) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    approved_at TIMESTAMP(6) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempt INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_until TIMESTAMP(6),
    outcome VARCHAR(30),
    last_error_type VARCHAR(100),
    delivered_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_gateway_card_notification_event UNIQUE (event_id),
    CONSTRAINT uk_gateway_card_notification_payment UNIQUE (pg_payment_id),
    CONSTRAINT fk_gateway_card_notification_payment FOREIGN KEY (pg_payment_id)
        REFERENCES gateway_payments(pg_payment_id),
    CONSTRAINT fk_gateway_card_notification_owner FOREIGN KEY (pg_payment_id, merchant_id)
        REFERENCES gateway_payments(pg_payment_id, merchant_id),
    CONSTRAINT chk_gateway_card_notification_status CHECK (status IN ('PENDING', 'SENDING', 'DELIVERED')),
    CONSTRAINT chk_gateway_card_notification_amount CHECK (amount > 0),
    CONSTRAINT chk_gateway_card_notification_attempt CHECK (attempt >= 0),
    CONSTRAINT chk_gateway_card_notification_lease CHECK ((status = 'SENDING') = (lease_until IS NOT NULL)),
    CONSTRAINT chk_gateway_card_notification_result CHECK (
        (status = 'DELIVERED' AND outcome IS NOT NULL AND outcome IN ('ACCEPTED', 'REJECTED') AND delivered_at IS NOT NULL)
        OR (status <> 'DELIVERED' AND outcome IS NULL AND delivered_at IS NULL)
    )
);

CREATE INDEX idx_gateway_card_notification_ready
    ON gateway_card_notification_outbox(status, next_attempt_at, notification_id);
CREATE INDEX idx_gateway_card_notification_lease
    ON gateway_card_notification_outbox(status, lease_until, notification_id);

-- PG 거래 기반 은행 입금의 가맹점 통보 전용 Outbox. 은행→PG 내부 Outbox가 아니다.
CREATE TABLE gateway_bank_notification_outbox (
    notification_id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL,
    pg_payment_id VARCHAR(44) NOT NULL,
    merchant_id BIGINT NOT NULL,
    merchant_payment_no VARCHAR(60) NOT NULL,
    buyer_reference VARCHAR(50) NOT NULL,
    notification_contract VARCHAR(40) NOT NULL,
    notification_target_key VARCHAR(100) NOT NULL,
    account_number VARCHAR(30) NOT NULL,
    bank_company VARCHAR(30) NOT NULL,
    depositor_name VARCHAR(50) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    deposited_at TIMESTAMP(6) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempt INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_until TIMESTAMP(6),
    outcome VARCHAR(30),
    last_error_type VARCHAR(100),
    delivered_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_gateway_bank_notification_event UNIQUE (event_id),
    CONSTRAINT uk_gateway_bank_notification_payment UNIQUE (pg_payment_id),
    CONSTRAINT fk_gateway_bank_notification_payment FOREIGN KEY (pg_payment_id)
        REFERENCES gateway_payments(pg_payment_id),
    CONSTRAINT fk_gateway_bank_notification_owner FOREIGN KEY (pg_payment_id, merchant_id)
        REFERENCES gateway_payments(pg_payment_id, merchant_id),
    CONSTRAINT chk_gateway_bank_notification_status CHECK (status IN ('PENDING', 'SENDING', 'DELIVERED')),
    CONSTRAINT chk_gateway_bank_notification_amount CHECK (amount > 0),
    CONSTRAINT chk_gateway_bank_notification_attempt CHECK (attempt >= 0),
    CONSTRAINT chk_gateway_bank_notification_lease CHECK ((status = 'SENDING') = (lease_until IS NOT NULL)),
    CONSTRAINT chk_gateway_bank_notification_result CHECK (
        (status = 'DELIVERED' AND outcome IS NOT NULL AND outcome IN ('APPLIED', 'RESERVATION_CANCELLED') AND delivered_at IS NOT NULL)
        OR (status <> 'DELIVERED' AND outcome IS NULL AND delivered_at IS NULL)
    )
);

CREATE INDEX idx_gateway_bank_notification_ready
    ON gateway_bank_notification_outbox(status, next_attempt_at, notification_id);
CREATE INDEX idx_gateway_bank_notification_lease
    ON gateway_bank_notification_outbox(status, lease_until, notification_id);

-- HTTP 경계의 세 전달 단계는 flow별로 독립적인 Outbox 행과 임대를 사용한다.
CREATE TABLE bank_transfer_outbox (
    outbox_id BIGSERIAL PRIMARY KEY,
    flow VARCHAR(40) NOT NULL,
    event_id VARCHAR(100) NOT NULL,
    pg_payment_id VARCHAR(44) NOT NULL,
    payload JSONB NOT NULL,
    result JSONB,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempt INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_until TIMESTAMP(6),
    last_error_type VARCHAR(100),
    delivered_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_bank_transfer_event UNIQUE (flow, event_id),
    CONSTRAINT uk_bank_transfer_operation UNIQUE (flow, pg_payment_id),
    CONSTRAINT fk_bank_transfer_payment FOREIGN KEY (pg_payment_id)
        REFERENCES gateway_payments(pg_payment_id),
    CONSTRAINT chk_bank_transfer_flow CHECK (flow IN ('BANK_DEPOSIT', 'BANK_EXPIRATION', 'BANK_CANCEL_REQUEST', 'MERCHANT_CANCEL_RESULT')),
    CONSTRAINT chk_bank_transfer_status CHECK (status IN ('PENDING', 'SENDING', 'DELIVERED')),
    CONSTRAINT chk_bank_transfer_attempt CHECK (attempt >= 0),
    CONSTRAINT chk_bank_transfer_lease CHECK ((status = 'SENDING') = (lease_until IS NOT NULL)),
    CONSTRAINT chk_bank_transfer_result CHECK (
        (status = 'DELIVERED' AND result IS NOT NULL AND delivered_at IS NOT NULL)
        OR (status <> 'DELIVERED' AND result IS NULL AND delivered_at IS NULL)
    )
);

CREATE INDEX idx_bank_transfer_ready ON bank_transfer_outbox(flow, status, next_attempt_at, outbox_id);
CREATE INDEX idx_bank_transfer_lease ON bank_transfer_outbox(flow, status, lease_until, outbox_id);

-- 은행은 전체 취소의 최초 성공/거절을 고정한다. Outbox가 아닌 수신 측 멱등 처리 결과다.
CREATE TABLE bank_virtual_account_cancellations (
    pg_payment_id VARCHAR(44) PRIMARY KEY,
    request_payload JSONB NOT NULL,
    result_payload JSONB NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_bank_cancel_payment FOREIGN KEY (pg_payment_id)
        REFERENCES gateway_payments(pg_payment_id)
);

-- 개별 PG 환불의 금융 결과와 미실행 철회 기록. 가맹점 범위는 PG 원거래로 결정한다.
CREATE TABLE gateway_bank_refunds (
    refund_id BIGSERIAL PRIMARY KEY,
    pg_payment_id VARCHAR(44) NOT NULL,
    refund_request_id VARCHAR(60) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    completed_at TIMESTAMP(6),

    CONSTRAINT uk_gateway_bank_refund_request UNIQUE (pg_payment_id, refund_request_id),
    CONSTRAINT fk_gateway_bank_refund_payment FOREIGN KEY (pg_payment_id)
        REFERENCES gateway_payments(pg_payment_id),
    CONSTRAINT chk_gateway_bank_refund_amount CHECK (amount > 0),
    CONSTRAINT chk_gateway_bank_refund_status CHECK (status IN ('REQUESTED', 'SUCCEEDED', 'WITHDRAWN')),
    CONSTRAINT chk_gateway_bank_refund_completion CHECK (
        (status = 'REQUESTED' AND completed_at IS NULL)
        OR (status <> 'REQUESTED' AND completed_at IS NOT NULL)
    )
);

CREATE TABLE bank_virtual_account_refunds (
    refund_id BIGSERIAL PRIMARY KEY,
    pg_payment_id VARCHAR(44) NOT NULL,
    refund_request_id VARCHAR(60) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    completed_at TIMESTAMP(6) NOT NULL,

    CONSTRAINT uk_bank_virtual_account_refund_request UNIQUE (pg_payment_id, refund_request_id),
    CONSTRAINT fk_bank_virtual_account_refund_account FOREIGN KEY (pg_payment_id)
        REFERENCES dummy_virtual_accounts(pg_payment_id),
    CONSTRAINT chk_bank_virtual_account_refund_amount CHECK (amount > 0),
    CONSTRAINT chk_bank_virtual_account_refund_status CHECK (status IN ('SUCCEEDED', 'WITHDRAWN'))
);

-- 기존 은행 환불·카드 원승인 데이터는 유지하고 카드 개별 환불 기록만 추가한다.


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

CREATE TABLE card_approval_gates (
    pg_payment_id VARCHAR(44) PRIMARY KEY,
    buyer_reference VARCHAR(50) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_card_approval_gate_payment FOREIGN KEY (pg_payment_id)
        REFERENCES gateway_payments(pg_payment_id),
    CONSTRAINT chk_card_approval_gate_amount CHECK (amount > 0)
);
