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
  "userId": "IU",
  "cardCompany": "SHINHAN",
  "cardNumberLast4": "1111",
  "approvedAmount": 180000,
  "currentMonthUsedAmount": 180000,
  "limitAmount": 1000000,
  "approved": true,
  "message": "카드 결제와 티켓 결제 완료 반영이 완료되었습니다."
}
```

처리 규칙:

```text
1. paymentNo 중복 이력이 있으면 기존 이력 상태 검증
2. 새 결제번호면 사용자, 카드번호, CVC, 카드 비밀번호, 금액, 한도 검증
3. 카드 승인 성공 이력 저장
4. ticket-service 내부 카드 완료 API 호출
5. 성공 시 gateway card history = TICKET_PAYMENT_COMPLETED
6. 실패 시 카드 승인 취소, gateway card history = CANCELLED, ticket-service 실패 반영 요청
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
POST /ticket/api/v1/payments/internal/card/complete
POST /ticket/api/v1/payments/internal/card/fail
POST /ticket/api/v1/payments/internal/virtual-account/issued
POST /ticket/api/v1/payments/internal/virtual-account/deposit/complete
```

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
- 카드 gateway에 이미 실패/취소 이력이 있는 `paymentNo`로 다시 승인 요청하면 새 결제번호로 재시도해야 한다.
- `TICKET_PAYMENT_FAILED` 상태의 gateway 가상계좌 수동 재처리 API는 운영/정산 범위로 보고 현재 구현에서는 보류한다.
