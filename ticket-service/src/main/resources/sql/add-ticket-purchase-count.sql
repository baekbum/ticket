-- 기존 잠금 테이블에 매수를 추가한다. 예매 관련 쓰기를 중단한 상태에서 배포 전에 실행한다.
BEGIN;

ALTER TABLE ticket_purchase_locks
    ADD COLUMN IF NOT EXISTS ticket_count BIGINT NOT NULL DEFAULT 0;

UPDATE ticket_purchase_locks AS purchase
SET ticket_count = (
    SELECT COUNT(*) FROM tickets AS ticket
    JOIN events AS event ON event.event_id = ticket.event_id
    WHERE ticket.user_id = purchase.user_id
      AND ticket.status IN ('PENDING_PAYMENT', 'PAID')
      AND (
          (purchase.limit_scope = 'PER_EVENT' AND CAST(ticket.event_id AS VARCHAR) = purchase.scope_key)
          OR (purchase.limit_scope = 'PER_GROUP' AND event.event_group_code = purchase.scope_key)
      )
);

ALTER TABLE ticket_purchase_locks
    ADD CONSTRAINT ck_ticket_purchase_locks_count CHECK (ticket_count >= 0);

COMMIT;
