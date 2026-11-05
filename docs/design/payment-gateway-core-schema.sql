-- PG 가맹점·거래·식별자 설계의 핵심 테이블 DDL 초안.
-- 실행용 마이그레이션이 아니다. 기존 DB에 직접 적용하지 않는다.
-- 핵심 모델은 src/main/resources/sql/schema.sql에 반영했다. 이 파일은 설계 기록이다.
-- 기존 DB 적용은 src/main/resources/sql/add-merchants-gateway-payments.sql을 사용한다.
-- 금융 테이블 FK, 환불 키 전환, Outbox 확장과 데이터 이전 SQL은 구현 단계에서 함께 작성한다.
-- UUID는 애플리케이션에서 생성한다. 비밀값과 운영 merchant seed는 포함하지 않는다.

CREATE TABLE merchants (
    -- 숫자 PK 자체를 가맹점 코드로 사용한다. 가맹점명은 중복을 허용한다.
    merchant_id BIGSERIAL PRIMARY KEY,
    merchant_name VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SUSPENDED',
    credential_key VARCHAR(64) NOT NULL,
    notification_adapter VARCHAR(40) NOT NULL,
    notification_target_key VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_merchants_credential_key UNIQUE (credential_key),
    CONSTRAINT chk_merchants_id CHECK (merchant_id > 0),
    CONSTRAINT chk_merchants_name CHECK (btrim(merchant_name) <> ''),
    CONSTRAINT chk_merchants_credential_key CHECK (btrim(credential_key) <> ''),
    CONSTRAINT chk_merchants_notification_config CHECK (
        btrim(notification_adapter) <> '' AND btrim(notification_target_key) <> ''
    ),
    CONSTRAINT chk_merchants_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TABLE gateway_payments (
    pg_payment_id UUID PRIMARY KEY,
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
    pg_payment_id UUID NOT NULL,
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
