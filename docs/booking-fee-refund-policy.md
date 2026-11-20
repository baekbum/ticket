# 예매 당일 예매 수수료 반환

한국 시간(`Asia/Seoul`)의 예매일(`Reservation.reservedAt`)과 최초 환불 신청일이 같으면 취소 대상 좌석분의 예매 수수료도 반환한다. 24시간 이내 정책이 아니며, 날짜가 달라지면 기존처럼 예매 수수료를 제외한다. 기존 취소 마감 시각과 취소 수수료·쿠폰·배송비 정책은 유지한다. 카드와 입금 완료된 가상계좌 모두 같은 계산을 사용한다.

## 계산과 부분 환불

- 결제액 187,200원(티켓 180,000원 + 예매 수수료 4,000원 + 배송비 3,200원)을 당일 전체 취소하면 187,200원을 반환한다.
- 다음 날 전체 취소하면 취소 수수료가 면제되는 기간에도 예매 수수료는 제외하여 183,200원을 반환한다.
- 부분 취소는 최초 `PaymentSnapshot.tickets`의 좌석 수에 따라 예매 수수료를 나눈다. 원 단위 나머지는 최초 스냅샷 순서대로 배분하므로 취소 순서와 무관하게 원래 합계를 보존한다.
- 당일 취소한 좌석분만 반환한다. 남은 좌석을 다음 날 취소하면 그 좌석분의 예매 수수료는 반환하지 않는다.

`PaymentRefundSnapshot.reservationFeeRefundAmount`는 `refundAmount`에 이미 포함된 예매 수수료 반환분이다. 최초 신청 트랜잭션에서 금액·대상·계산 시각과 함께 저장하고 재시도 시 재계산하지 않는다. 당일 신청의 응답이 유실되어 다음 날 재시도해도 같은 환불 프로세스 ID와 최초 금액을 사용한다. 철회 후 새 신청을 접수하면 새 신청일을 기준으로 계산한다.

`Payment.reservationFeeRefundedAmount`는 로컬 성공 반영이 끝난 누적 예매 수수료 반환액이다. 금융 성공을 확인한 뒤 티켓·좌석·결제·성공 이력과 같은 트랜잭션에서 증가한다. 로컬 처리 실패 시 함께 롤백하고 재시도에서 한 번만 반영한다. 반환 가능 잔액은 다음과 같다.

```text
최초 결제액 - 누적 환불액 - 누적 취소 수수료
    - (최초 예매 수수료 - 이미 반환한 예매 수수료)
```

최초 결제 티켓 스냅샷이 없거나 대상 좌석과 맞지 않으면 당일 수수료를 임의 계산하지 않고 요청을 거절한다. PG·카드·은행에는 기존 계약대로 총 환불 금액을 보내며 예매 수수료 정책은 Ticksy가 담당한다. 모든 티켓 취소가 완료되어도 수수료를 보유한 경우 Ticksy의 `REFUNDED`와 금융 도메인의 `PARTIALLY_REFUNDED`는 구분한다.

## 스키마와 기존 데이터

정식 생성 쿼리는 `ticket-service/src/main/resources/sql/schema.sql`에 반영했다. 기존 DB에는 다음 두 컬럼만 추가하며 테이블 재생성이나 데이터 초기화가 필요 없다.

```sql
ALTER TABLE payments
    ADD COLUMN reservation_fee_refunded_amount INTEGER NOT NULL DEFAULT 0
        CHECK (reservation_fee_refunded_amount >= 0);

ALTER TABLE payment_refund_snapshots
    ADD COLUMN reservation_fee_refund_amount INTEGER NOT NULL DEFAULT 0
        CHECK (reservation_fee_refund_amount >= 0);
```

기존 신청 스냅샷과 누적 반환분은 0으로 유지한다. 이미 완료된 환불이나 처리 중인 신청 금액을 변경하지 않으며 과거 결제에 추가 환불을 자동 실행하지 않는다. 배포 시 컬럼을 먼저 추가하고 변경된 티켓 서비스를 시작한다.
