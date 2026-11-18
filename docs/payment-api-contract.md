# 결제 API 계약

> 2026-11-18: 구형 paymentNo 전용 PG/금융 API와 X-Service-Token 전용 PG 인증은 폐쇄했다. 아래의 구형 경로 설명은 이전 구조 기록이며 호출 계약으로 사용하지 않는다. 현재 변경 범위와 보존한 API는 [레거시 정리 기록](payment-legacy-retirement.md)을 따른다. 은행 입금은 `/api/v1/banks/virtual-accounts/deposit`, 환불은 신규 PG 거래·가맹점 범위 API를 사용한다.

2026-11-16 신규 카드→무통장 전환은 카드사의 승인 차단과 기존 성공 복구를 거친다. [전환 API·세션 무효화·경합 계약](payment-gateway-card-method-change.md)을 따른다.

2026-11-15 신규 PG 카드의 전액·부분 환불과 신청 철회를 공통 가맹점 환불 API에 연결했다. [카드 환불·재시도·철회 계약](payment-gateway-card-refund.md)을 우선한다.

2026-11-15 Ticksy 신규 카드 checkout의 PG prepare·ID 저장과 브라우저의 거래 전용 세션 승인·조회, 공통 결과 통보를 연결했다. 실제 호출·인증·응답 유실 복구·기존 카드 호환 및 현재 수단 전환/환불 제한은 [카드 checkout 연동](ticksy-gateway-card-checkout.md)을 우선한다.

2026-11-13 신규 PG 연결 가상계좌의 입금 후 전액·부분 환불과 명시적 신청 철회를 구현했다. Ticksy→PG는 1회, PG→은행은 최대 4회이며 신청 스냅샷과 같은 키로 재요청한다. 새 API·NOWAIT 잠금·스키마·기존 경로의 호환성 범위는 [환불·신청 철회 계약](payment-gateway-bank-refund.md)을 우선한다. 아래 초기 설계의 환불 후속 작업 설명은 이 문서의 현재 구현으로 대체한다.

2026-11-12 신규 PG 가상계좌 만료는 은행→PG의 금융 인증 Outbox로 확정한다. Ticksy는 기존 가맹점 범위 GET 거래 조회에서 EXPIRED를 확인한 후에만 만료한다. PG→Ticksy 만료 통보 API는 추가하지 않는다. [만료 계약·입금 경합·운영 범위](payment-gateway-account-expiration.md)를 참고한다.

2026-11-12 계좌 발급은 같은 checkout HTTP 요청 안에서 최초 1회 + 추가 3회만 시도한다. 일시 오류가 계속되면 503 GATEWAY_ACCOUNT_ISSUE_UNCONFIRMED를 반환하고 사용자가 같은 checkout 멱등키로 재요청한다. 일반 발급 디스패쳐는 제거했으며 입금·취소 Outbox는 유지한다. [현재 발급 재시도 계약](ticksy-gateway-virtual-account.md)을 참고한다.

2026-11-11 Ticksy 신규 무통장 checkout은 가맹점 인증 PG prepare → pgPaymentId 저장 → 계좌 발급으로 전환했다. 취소 Outbox는 신규 PG ID 경로의 202 접수와 최종 통보를 구분한다. 기존 checkout URL은 유지하고 PaymentResponse에 nullable pgPaymentId를 추가한다. [Ticksy 호출·스키마·현재 제한](ticksy-gateway-virtual-account.md)을 우선한다. 신규 PG 가상계좌 환불은 별도 신청·철회 API로 연결했으며 만료는 PG 상태 조회로 연결했다.

2026-11-11 최신 구조: 입금은 BankController의 은행 업무 API다. POST /api/v1/banks/virtual-accounts/deposit에서 은행 상태·입금 이력·은행→PG Outbox를 함께 저장하고, PG는 수신 후 Ticksy에 별도 Outbox로 통보한다. Postman·Bruno도 같은 은행 API를 호출한다. 별도 테스트 컨트롤러와 support 입금 URL은 제거했다. 기존 PG 미연결 계좌의 legacy/deposit 경로는 폐쇄했다. 인증은 기존 X-Payment-Provider-Token을 유지하며 PG→은행의 기존 내부 업무 URL과 금융 인증도 유지한다. [은행 API 상세](payment-gateway-bank-api.md)를 참고한다.

2026-11-09 신규 PG 카드 결과 통보는 [공통 요청/응답 계약](payment-gateway-standard-card-notification.md)을 사용한다. PG는 등록된 전체 URL을 공통 send 인자로 전달하고, Ticksy는 POST /api/v1/payments/internal/gateway/card/notifications에서 수신한다. 기존 settle 엔드포인트는 유지한다.

2026-11-08 PG 거래 기반 카드 승인/조회 API와 가맹점 카드 통보 Outbox를 추가했다. POST /api/v1/payments/card/gateway/{pgPaymentId}/approve와 GET /api/v1/payments/card/gateway/{pgPaymentId}는 가맹점 서버 인증을 사용한다. 금융 승인 PAID와 통보 완료는 별도 상태다. 요청·응답·보상 계약은 [PG 카드 승인](payment-gateway-card-approval.md)을 따른다. 기존 사용자 JWT 카드 API와 Ticksy 호출은 유지한다.

PG의 가맹점 인증 기반 거래 준비·조회 API가 추가되었다. 2026-11-13부터 신규 PG 경로는 Merchant·credentialKey 헤더와 DB 해시로 인증한다. [인증 및 키 발급 계약](payment-gateway-merchant-authentication.md)을 따른다. 경로·요청·상태 코드와 현재 적용 범위는 [PG 기반 구현](payment-gateway-core-implementation.md)을 따른다. 기존 금융 API와 신규 공통 PG API는 단계적으로 연결한다.

2026-11-08 가맹점 인증 기반 `POST /api/v1/payments/virtual-account/gateway/{pgPaymentId}/issue`를 추가했다. 준비된 PG 거래를 은행 계좌에 연결하고 PG 상태/발급 이력을 저장한다. 요청·응답·복구 계약은 [PG 거래 기반 계좌 발급](payment-gateway-account-issuance.md)을 따른다. 현재 Ticksy 신규 발급·입금·입금 전 취소는 이 경로로 전환했다. 새 연결 거래의 환불은 환불·신청 철회 계약을 따르며 만료는 은행 확정 상태와 Ticksy 조회로 처리한다.

2026-11-06부터 기존 카드·가상계좌 외부 API는 [모의 금융 HTTP 경계](payment-gateway-financial-http.md)를 통해 처리한다. URL·입력·JSON·상태 코드는 유지하고 PG가 RestClient로 내부 전용 모의 컨트롤러를 호출한다. 금융 결과를 확인할 수 없는 연결/읽기 실패는 `503 FINANCIAL_PROVIDER_UNAVAILABLE`이며 같은 거래의 상태 확인/멱등 재요청 대상으로 취급한다.

신규 `pgPaymentId`는 `yyyyMMdd + UUID`로 생성한 44자 문자열이며 자바 타입은 String, DB 컬럼은 VARCHAR(44)다. 날짜는 한국 시간 기준이고 날짜와 UUID 사이 구분자는 없다. 예: `20261105550e8400-e29b-41d4-a716-446655440000`. 기존 36자 UUID 식별자의 조회도 유지한다.

현재 결제 흐름은 `ticket-service`가 checkout과 최종 결제 상태를 관리하고, `payment-gateway-service`가 카드 승인/가상계좌 발급/입금 확인을 담당한다.

## 공통 규칙

- 사용자는 `ticket-service`의 `CheckoutController.prepare`로 결제 화면 진입 가능 여부를 검증한다.
- `CheckoutController.confirm`은 예약, 배송, 결제 row를 생성하고 `paymentNo`를 반환한다.
- 카드 승인과 승인 상태 조회는 사용자 access token으로 `payment-gateway-service`에 직접 요청한다.
- 신규 PG 가상계좌 준비·조회·발급·취소는 `ticket-service`가 `Merchant`·`credentialKey`로 요청한다. 구형 발급·조회·취소·환불 계약과 Ticksy의 구형 입금 완료·관리자 정합성 API는 폐쇄했다.
- 가상계좌 테스트 입금 발생은 입금 제공자가 `X-Payment-Provider-Token`으로 `payment-gateway-service`에 요청한다.
- `payment-gateway-service`는 성공/실패 결과를 `ticket-service` 내부 API로 반영한다.
- 서비스 간 내부 API는 `X-Service-Token` 헤더로 보호한다.

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

카드 승인 요청에는 명의자 이름을 받지 않는다. 카드사와 카드번호로 카드를 조회한 뒤 CVC·비밀번호·만료·한도를 검증한다. 카드 소유자와 로그인한 예매자는 달라도 되며, ticket 검증/완료 요청과 결제 이력의 userId는 로그인한 예매자 기준이다. 다른 사람 카드로 결제한 경우 응답의 currentMonthUsedAmount와 limitAmount는 null이다.

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
X-Service-Token: {internalServiceToken}
Content-Type: application/json
```

```json
{
  "paymentRefundProcessId": 123,
  "paymentNo": "PAY-...",
  "transactionId": "CARD-...",
  "refundAmount": 250000
}
```

처리 규칙:

1. paymentRefundProcessId가 없거나 양수가 아니면 거부한다.
2. 같은 ID의 성공 기록이 있으면 결제번호·금액·거래번호 일치를 검증하고 최초 응답을 반환한다.
3. 신규 요청은 카드 승인 이력을 잠근 뒤 같은 ID의 성공 기록을 다시 확인한다.
4. 환불 가능한 결제 상태와 남은 환불 금액을 검증한다. 부분 환불도 허용한다.
5. 카드 사용액 차감, 누적 환불액 변경, gateway_refunds 기록을 같은 트랜잭션으로 커밋한다.
6. ticket-service는 PG 성공 후 결제·예매·티켓·좌석의 취소를 반영한다.

가상계좌 환불도 같은 프로세스 ID를 사용하고 환불 은행·계좌번호·예금주 일치를 검증한다. 상세 동시성·배포 규칙은 [환불 멱등성](refund-idempotency.md)을 참고한다.

## Gateway 가상계좌 입금

```http
POST /payment-gateway/api/v1/banks/virtual-accounts/legacy/deposit
X-Payment-Provider-Token: {paymentProviderToken}
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
  "status": "DEPOSITED",
  "expiresAt": "2026-08-20T23:59:59",
  "depositedAt": "2026-08-20T12:00:00",
  "message": "가상계좌 입금이 완료되었습니다. 티켓 서비스에 입금 정보를 전달합니다."
}
```

처리 규칙:

```text
1. accountNumber로 gateway 가상계좌 조회
2. WAITING_DEPOSIT 상태, 입금 금액, 만료 시각 검증
3. gateway virtual account = DEPOSITED
4. 입금 이력과 PENDING 입금 Outbox 저장 후 함께 커밋
5. 커밋 이후 ticket-service 내부 무통장 입금 완료 API 호출
6. 일치하는 성공 응답을 확인하면 Outbox = DELIVERED, 계좌는 DEPOSITED 유지
7. 전달 실패·응답 유실은 PENDING 유지 후 스케줄러로 동일 입금 정보를 재전달
8. 동일 입금 재요청은 기존 이력·Outbox 재사용, 다른 입금 정보는 거부
```

## ticket-service 내부 API

gateway가 ticket-service에 결제 상태를 반영할 때만 사용한다.

가상계좌 발급 정보는 ticket-service가 checkout 중 PG 발급 응답을 받아 직접 저장한다. 발급 요청은 결제번호·은행사·금액·공연일시를 전달한다.

```text
X-Service-Token: {internalServiceToken}
Content-Type: application/json
```

```http
POST /ticket/api/v1/payments/internal/card/validate
POST /ticket/api/v1/payments/internal/card/settle
POST /ticket/api/v1/payments/internal/card/complete
POST /ticket/api/v1/payments/internal/card/fail
POST /ticket/api/v1/payments/internal/virtual-account/deposit/complete
```

### 가상계좌 내부 입금·취소 계약

가상계좌 입금 완료 내부 응답은 `{"result":"APPLIED","payment":{...}}`다. 티켓의 예약과 결제가 모두 취소된 경우에는 `{"result":"RESERVATION_CANCELLED","payment":{"paymentNo":"PAY-...","status":"CANCELLED",...}}`를 HTTP 200으로 반환한다. PG는 이를 장애와 구분하여 계좌 취소와 입금 통지 완료를 함께 처리한다. 양 서비스는 이 응답 계약을 함께 배포한다. [입금 전 취소 Outbox](virtual-account-cancellation-outbox.md)를 참고한다.

티켓에서 PG로 보내는 계좌 취소는 내부 서비스 토큰을 사용하는 `POST /payment-gateway/api/v1/payments/virtual-account/cancel`이다. 요청은 `paymentNo`, `bankName`, `accountNumber`, `amount`를 포함한다. PG는 계좌 정보를 검증하고 같은 정보와 `status: CANCELLED`를 반환한다. 이미 취소된 계좌도 동일하게 성공하며, 결제번호가 같은데 계좌 정보가 다르면 거부한다.

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

## PG 거래 기반 가상계좌 취소와 입금 Outbox

POST /api/v1/payments/virtual-account/gateway/{pgPaymentId}/cancel은 가맹점 인증과 원거래·예매자·금액·은행·계좌 검증 후 취소 요청 Outbox를 저장하고 HTTP 202 PROCESSING을 반환한다. 은행 확정 후 동일 요청은 HTTP 200 CANCELLED/REJECTED와 최초 사유를 반환한다. GET /api/v1/payments/virtual-account/gateway/{pgPaymentId}/cancellation으로 소유 가맹점만 접수/결과를 조회할 수 있다. 취소 가능 여부는 은행 책임이며 취소 경로에서 입금 처리를 호출하지 않는다.

은행 확정 결과·PG 상태/이력·은행 요청 Outbox DELIVERED·가맹점 결과 Outbox 저장은 하나의 PG 트랜잭션이다. 은행의 업무 거절도 전달은 완료하며 가맹점에 거절을 통보한다. 최종 통보는 기존 등록 URL에 X-PG-Event-Type: BANK_CANCELLATION과 BankCancellationNotification을 보내며, 같은 이벤트/금액/결과의 BankCancellationNotificationResponse를 확인한다. [취소·멱등성·트랜잭션 상세](payment-gateway-account-cancellation.md)를 따른다.

입금 제공자용 POST /api/v1/banks/virtual-accounts/deposit으로 분리했다. 새 PG 연결 계좌의 은행 입금·이력·은행→PG Outbox를 커밋한 뒤 응답하며 PG PAID나 가맹점 전달 완료를 보장하지 않는다. 은행은 POST /internal/financial-simulations/gateway/bank-deposits로 고정된 최초 입금을 통보한다. PG는 입금·이력·PG→가맹점 입금 Outbox를 커밋한 뒤 확인 응답한다. 은행과 가맹점 전달은 독립 재시도한다. 기존 NULL 연결 계좌는 기존 계약을 유지한다. [입금·지연 순서·복구 상세](payment-gateway-account-deposit.md)를 따른다.

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
- 관리자는 `GET /ticket/api/v1/manage/payment/virtual-account?page=0&paymentNo=PAY-&status=WAITING_DEPOSIT`로 Ticket의 가상계좌 결제를 최신 요청순(같은 시각이면 결제 ID 역순) 20건씩 조회한다. 결제번호와 상태 필터는 선택 사항이며, 목록 조회 중 PG는 호출하지 않는다.
- 목록에서 결제 건을 선택하면 `GET /ticket/api/v1/manage/payment/virtual-account/{paymentNo}/reconciliation`로 Ticket·PG 상태를 함께 확인한다.
- PG의 `GET /api/v1/payments/virtual-account/internal/{paymentNo}/status`는 내부 서비스 토큰으로만 호출한다.
- 관리자 `POST /ticket/api/v1/manage/payment/virtual-account/{paymentNo}/complete`는 처리 사유를 받고 PG 상태를 다시 조회한다. Ticket이 `WAITING_DEPOSIT`이고 입금 정보와 예약·티켓·좌석 상태가 일치할 때만 전체 결제 완료 로직을 실행한다. 이미 만료·취소된 건은 자동 완료하지 않는다.
- 배포 시 PG의 내부 상태 조회 API를 먼저 반영하고, 이후 Ticket API와 관리자 화면을 반영한다.

2026-11-16: 미발급 무통장 거래의 카드 전환 계약은 [무통장→카드 전환](payment-gateway-bank-method-change.md)을 참고한다. 발급된 계좌는 상태와 무관하게 변경 불가다.
