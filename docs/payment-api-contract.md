# 결제 API 계약

현재 결제 흐름은 `ticket-service`가 checkout과 최종 결제 상태를 관리하고, `payment-gateway-service`가 카드 승인/가상계좌 발급/입금 확인을 담당한다.

## 공통 규칙

- 사용자는 `ticket-service`의 `CheckoutController.prepare`로 결제 화면 진입 가능 여부를 검증한다.
- `CheckoutController.confirm`은 예약, 배송, 결제 row를 생성하고 `paymentNo`를 반환한다.
- 카드 승인과 가상계좌 입금 확인은 `payment-gateway-service`로 요청한다.
- `payment-gateway-service`는 성공/실패 결과를 `ticket-service` 내부 API로 반영한다.
- `ticket-service` 내부 API는 `X-Service-Token` 헤더로 보호한다.

```text
Authorization: Bearer {accessToken}
Content-Type: application/json
```

## Checkout 준비

```http
POST /ticket/api/v1/checkout/prepare
Authorization: Bearer {accessToken}
X-Active-Token: {activeToken}
Content-Type: application/json
```

성공 시 서버가 `idempotencyKey`를 발급한다. 프론트는 결제 화면에 머무르는 동안 이 값을 `confirm` 요청에 사용한다.

```json
{
  "orderId": "ORDER-1",
  "eventId": 1,
  "seats": [
    { "id": 1, "zone": "VIP", "row": 1, "col": 1 }
  ]
}
```

## Checkout 확정

```http
POST /ticket/api/v1/checkout/confirm
Authorization: Bearer {accessToken}
Content-Type: application/json
```

카드 결제는 `Payment.status=READY`로 생성하고 `paymentNo`를 반환한다. 무통장 결제는 gateway에서 계좌 발급 후 `Payment.status=WAITING_DEPOSIT`로 반영한다.

```json
{
  "orderId": "ORDER-1",
  "eventId": 1,
  "seats": [
    { "id": 1, "zone": "VIP", "row": 1, "col": 1 }
  ],
  "delivery": {
    "recipientName": "홍길동",
    "recipientPhone": "010-0000-0000",
    "zipCode": "12345",
    "address": "서울시 강남구",
    "detailAddress": "101호"
  },
  "paymentMethod": "CREDIT_CARD",
  "idempotencyKey": "CHK-...",
  "bankCode": null
}
```

## Gateway 카드 승인

```http
POST /payment-gateway/api/v1/payments/card/approve
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "paymentNo": "PAY-...",
  "cardCompany": "SHINHAN",
  "cardNumber": "4111-1111-1111-1111",
  "cvc": "516",
  "cardPassword": "1234",
  "customerName": "아이유",
  "amount": 180000
}
```

성공 응답:

```json
{
  "paymentNo": "PAY-...",
  "transactionId": "CARD-...",
  "userId": "IU",
  "cardCompany": "SHINHAN",
  "maskedCardNumber": "4111-****-****-1111",
  "approvedAmount": 180000,
  "currentMonthUsedAmount": 180000,
  "limitAmount": 1000000,
  "approved": true,
  "message": "카드 결제와 티켓 결제 완료 반영이 완료되었습니다."
}
```

처리 규칙:

1. 기존 paymentNo 이력이 있으면 소유자와 금액을 검증한다. APPROVED/TICKET_PAYMENT_FAILED는 기존 거래로 완료 반영을 재시도하고, 완료된 이력은 기존 결과를 반환한다.
2. 신규 요청은 카드 정보를 검증하고 ticket 내부 `/card/validate`로 결제 가능 여부 및 서버 금액을 확인한다.
3. 요청 금액이 서버 금액과 같은지 확인하고 서버 금액으로 카드 사용액과 APPROVED 이력을 커밋한다.
4. 별도 트랜잭션에서 ticket 내부 `/card/settle`로 결제 완료를 요청한다.
5. COMPLETED면 TICKET_PAYMENT_COMPLETED로 커밋하고 HTTP 200을 반환한다.
6. REJECTED와 결제의 종료 상태를 확인한 경우에만 승인 취소를 커밋하고 HTTP 400을 반환한다.
7. 통신 오류 또는 gateway 완료/취소 커밋 실패는 승인 이력을 유지하고 HTTP 202를 반환한다. 서버는 자동 재처리한다.

확인 대기 응답 (HTTP 202, 결제 성공으로 취급하면 안 됨):

```json
{
  "paymentNo": "PAY-...",
  "status": "PENDING",
  "message": "카드 승인 후 예매 결과를 확인하고 있습니다. 새 결제를 시작하지 말고 상태를 조회해주세요."
}
```

### Gateway 카드 상태 조회

```http
GET /payment-gateway/api/v1/payments/card/{paymentNo}
Authorization: Bearer {accessToken}
```

인증 사용자 본인의 승인 이력만 반환한다.

```json
{
  "paymentNo": "PAY-...",
  "status": "TICKET_PAYMENT_COMPLETED",
  "transactionId": "CARD-..."
}
```

- APPROVED / TICKET_PAYMENT_FAILED: 결과 확인 대기. 같은 paymentNo로 조회를 계속한다.
- TICKET_PAYMENT_COMPLETED: 완료 화면 이동.
- CANCELLED: 승인 취소 안내. 새 결제 준비가 필요하다.
- REFUNDED / PARTIALLY_REFUNDED: 환불 상태 안내.
- APPROVAL_FAILED: 과거 승인 실패 이력. 새 결제번호가 필요하다.
- 승인 이력이 아직 없으면 HTTP 400이다. 승인 API 응답 유실 직후라면 아직 실행 중일 수 있으므로 새로운 paymentNo를 만들지 말고 같은 요청을 재시도한다.

## Gateway 카드 전체 환불

ticket-service가 카드 결제 완료 예매를 전체 취소할 때 사용한다. 예매/티켓/좌석 상태를 바꾸기 전에 gateway 환불을 먼저 완료한다.

```http
POST /payment-gateway/api/v1/payments/card/refund
Content-Type: application/json
```

```json
{
  "paymentNo": "PAY-...",
  "transactionId": "CARD-...",
  "refundAmount": 250000
}
```

처리 규칙:

```text
1. paymentNo + transactionId로 gateway 카드 승인 이력 조회
2. gateway card history = TICKET_PAYMENT_COMPLETED 상태 검증
3. 전체 환불 금액이 승인 금액과 일치하는지 검증
4. DummyCard.currentMonthUsedAmount에서 환불 금액 차감
5. gateway card history = REFUNDED
6. ticket-service는 환불 성공 후 Payment.status=REFUNDED, 예매/티켓=CANCELLED, 좌석=AVAILABLE 반영
```

## Gateway 가상계좌 입금

```http
POST /payment-gateway/api/v1/payments/virtual-account/deposit
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "accountNumber": "1111-2222-3333",
  "depositorName": "홍길동",
  "amount": 180000,
  "depositedAt": "2026-08-20T12:00:00"
}
```

성공 응답:

```json
{
  "paymentNo": "PAY-...",
  "bankCompany": "KB",
  "bankName": "KB국민은행",
  "accountNumber": "1111-2222-3333",
  "depositorName": "홍길동",
  "amount": 180000,
  "status": "TICKET_PAYMENT_COMPLETED",
  "expiresAt": "2026-08-20T23:59:59",
  "depositedAt": "2026-08-20T12:00:00",
  "message": "가상계좌 입금과 티켓 결제 완료 반영이 완료되었습니다."
}
```

처리 규칙:

```text
1. accountNumber로 gateway 가상계좌 조회
2. WAITING_DEPOSIT 상태, 입금 금액, 만료 시각 검증
3. gateway virtual account = DEPOSITED
4. 입금 이력 저장
5. ticket-service 내부 무통장 입금 완료 API 호출
6. 성공 시 gateway virtual account = TICKET_PAYMENT_COMPLETED
7. 실패 시 gateway virtual account = TICKET_PAYMENT_FAILED, 실패 사유 저장
```

## ticket-service 내부 API

gateway가 ticket-service에 결제 상태를 반영할 때만 사용한다.

```text
X-Service-Token: {internalServiceToken}
Content-Type: application/json
```

```http
POST /ticket/api/v1/payments/internal/card/validate
POST /ticket/api/v1/payments/internal/card/settle
POST /ticket/api/v1/payments/internal/card/complete
POST /ticket/api/v1/payments/internal/card/fail
POST /ticket/api/v1/payments/internal/virtual-account/issued
POST /ticket/api/v1/payments/internal/virtual-account/deposit/complete
```

### 카드 내부 검증/완료 계약

`/card/validate` 요청은 `{"paymentNo":"PAY-...","userId":"user01"}`이며 userId는 gateway의 인증 컨텍스트에서 가져온다. 응답은 READY 상태의 PaymentResponse이다. 카드번호/CVC는 ticket으로 전달하지 않는다.

`/card/settle` 요청은 기존 CardPaymentCompleteRequest(paymentNo, userId, amount, transactionId, cardCompany, maskedCardNumber)이다. 응답은 다음과 같다.

```json
{
  "outcome": "COMPLETED",
  "payment": {"paymentNo": "PAY-...", "amount": 180000, "status": "PAID", "cardTransactionId": "CARD-..."}
}
```

outcome은 COMPLETED 또는 REJECTED다. REJECTED는 ticket이 EXPIRED/CANCELLED/FAILED 상태임을 확정한 응답이며, HTTP 오류/타임아웃과 구분한다. 이미 PAID/REFUNDED/PARTIALLY_REFUNDED인 경우 동일 거래번호만 COMPLETED로 인정한다. 기존 `/card/complete`와 `/card/fail`은 호환성을 위해 유지하며 새 gateway 승인 흐름은 `/card/settle`을 사용한다.

ticket의 신규 내부 API를 먼저 배포한 뒤 gateway를 배포해야 한다. 브라우저는 HTTP 202와 상태 조회 계약을 반영해야 한다.

## 무통장 만료 이벤트

무통장 만료는 사용자 요청이 아니라 스케줄러 기반 후처리이므로 Kafka outbox로 처리한다.

```text
gateway scheduler
-> WAITING_DEPOSIT + expiresAt 지난 가상계좌 만료
-> virtual_account_outbox_events PENDING 저장
-> outbox publisher가 VIRTUAL_ACCOUNT_EXPIRED 이벤트 발행
-> ticket-service consumer가 Payment.status=EXPIRED 반영
```

## 재시도 정책

- `READY`, `WAITING_DEPOSIT`, `PAID` 상태의 같은 `idempotencyKey`는 기존 결제를 반환한다.
- `FAILED`, `CANCELLED`, `EXPIRED` 이후 같은 `idempotencyKey`로 `confirm`하면 새 `Payment` row와 새 `paymentNo`를 생성한다.
- 카드 입력/사전 검증 실패는 신규 이력을 남기지 않으며 READY의 같은 paymentNo로 재시도한다. APPROVED/TICKET_PAYMENT_FAILED는 재승인 없이 완료 반영만 재시도한다. CANCELLED 또는 과거 APPROVAL_FAILED 이력은 새 결제번호가 필요하다.
- `TICKET_PAYMENT_FAILED` 상태의 gateway 가상계좌 수동 재처리 API는 운영/정산 범위로 보고 현재 구현에서는 보류한다.
