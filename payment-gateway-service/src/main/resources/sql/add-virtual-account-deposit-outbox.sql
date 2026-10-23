-- PG DB에서 실행한다. 입금 완료·입금 이력과 같은 트랜잭션에 저장할 HTTP 알림 Outbox.
BEGIN;

-- 제거한 enum 값이 남은 이력은 새 코드에서 읽을 수 없으므로 먼저 확인한다.
-- 과거 이력을 임의로 삭제하거나 입금 이력으로 변경하지 않는다.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM dummy_virtual_account_payment_histories
        WHERE history_type IN ('TICKET_PAYMENT_COMPLETED', 'TICKET_PAYMENT_FAILED')
    ) THEN
        RAISE EXCEPTION '기존 티켓 연동 이력이 남아 있습니다. 별도 보관·정리 후 다시 실행하세요.';
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS virtual_account_deposit_outbox (
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
CREATE INDEX IF NOT EXISTS idx_va_deposit_outbox_ready
    ON virtual_account_deposit_outbox(status, next_attempt_at, outbox_id);

-- 기존 입금의 미전달 건도 복구한다. 이미 전달이 확인된 계좌는 중복 전달하지 않는다.
INSERT INTO virtual_account_deposit_outbox (
    payment_no, status, bank_company, bank_name, account_number, depositor_name,
    amount, deposited_at, next_attempt_at, delivered_at
)
SELECT payment_no,
       CASE WHEN status = 'TICKET_PAYMENT_COMPLETED' THEN 'DELIVERED' ELSE 'PENDING' END,
       bank_company, bank_name, account_number, depositor_name, amount, deposited_at,
       CURRENT_TIMESTAMP,
       CASE WHEN status = 'TICKET_PAYMENT_COMPLETED' THEN updated_at ELSE NULL END
FROM dummy_virtual_accounts
WHERE status IN ('DEPOSITED', 'TICKET_PAYMENT_FAILED', 'TICKET_PAYMENT_COMPLETED')
  AND deposited_at IS NOT NULL AND depositor_name IS NOT NULL
ON CONFLICT (payment_no) DO NOTHING;

-- 계좌에는 입금 사실만 남기고, 전달 여부는 위 Outbox에서 관리한다.
UPDATE dummy_virtual_accounts SET status = 'DEPOSITED'
WHERE status IN ('TICKET_PAYMENT_COMPLETED', 'TICKET_PAYMENT_FAILED');

COMMIT;
