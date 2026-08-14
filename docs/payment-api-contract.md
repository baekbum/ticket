# 결제 API 계약

이 문서는 사용자 결제 시나리오 구현 전에 고정할 API 계약이다. 실제 PG 대신 백엔드가 카드 승인과 무통장 입금을 시뮬레이션한다.

## 공통 규칙

사용자 결제 API는 로그인 사용자의 예약에 대해서만 동작해야 한다.

```text
Authorization: Bearer {accessToken}
Content-Type: application/json
```

active token은 체크아웃 준비 성공 시점에 회수한다. 카드 승인과 가상계좌 발급 단계는 `paymentNo`, 예약 소유자, 결제 상태, 결제 만료 시간으로 검증한다.

성공 응답은 기존 `PaymentResponse`를 기본으로 사용한다.

```json
{
  "paymentId": 1,
  "reservationId": 1,
  "orderId": "ORDER-...",
  "paymentNo": "PAY-...",
  "method": "CREDIT_CARD",
  "status": "PAID",
  "amount": 180000,
  "bankName": null,
  "accountNumber": null,
  "depositorName": null,
  "requestedAt": "2026-07-27 12:00:00",
  "paidAt": "2026-07-27 12:03:00",
  "expiresAt": null
}
```

실패 응답은 공통 `ErrorResponse`를 사용한다.

```json
{
  "code": "INVALID_REQUEST",
  "message": "카드 정보가 일치하지 않습니다.",
  "details": null
}
```

## 카드 결제 승인

```http
POST /api/v1/payments/card/approve
Authorization: Bearer {accessToken}
Content-Type: application/json
```

요청 DTO 후보: `CardPaymentApproveRequest`

```json
{
  "paymentNo": "PAY-...",
  "cardCompany": "KB",
  "cardNumber": "1234-5678-9012-3456",
  "cvc": "123",
  "cardPassword": "qwe123!"
}
```

처리 규칙:

```text
1. paymentNo로 Payment 조회
2. payment.reservation.userId와 현재 로그인 사용자 일치 검증
3. payment.status == READY 검증
4. 더미 카드 정보 검증
5. 성공하면 공통 결제 완료 처리 호출
6. 실패하면 Payment 상태는 READY로 유지
```

성공 응답: `PaymentResponse`

```json
{
  "paymentId": 1,
  "reservationId": 1,
  "orderId": "ORDER-...",
  "paymentNo": "PAY-...",
  "method": "CREDIT_CARD",
  "status": "PAID",
  "amount": 180000,
  "bankName": null,
  "accountNumber": null,
  "depositorName": null,
  "requestedAt": "2026-07-27 12:00:00",
  "paidAt": "2026-07-27 12:03:00",
  "expiresAt": null
}
```

주요 실패:

```text
400 INVALID_REQUEST     카드 정보 불일치, 결제 상태 부적합
403 FORBIDDEN           다른 사용자의 결제 요청
404 INTERNAL_SERVER_ERROR 또는 INVALID_REQUEST 현재 결제 조회 실패 처리 기준에 맞춤
```

## 가상계좌 발급

```http
POST /api/v1/payments/virtual-account/issue
Authorization: Bearer {accessToken}
Content-Type: application/json
```

요청 DTO 후보: `VirtualAccountIssueRequest`

```json
{
  "paymentNo": "PAY-...",
  "bankCode": "KB",
  "depositorName": "홍길동"
}
```

처리 규칙:

```text
1. paymentNo로 Payment 조회
2. payment.reservation.userId와 현재 로그인 사용자 일치 검증
3. payment.status == READY 검증
4. bankCode로 은행명과 계좌 prefix 결정
5. 랜덤 계좌번호 생성
6. Payment.bankName, accountNumber, depositorName, expiresAt 저장
7. Payment.status = WAITING_DEPOSIT
```

성공 응답: `PaymentResponse`

```json
{
  "paymentId": 1,
  "reservationId": 1,
  "orderId": "ORDER-...",
  "paymentNo": "PAY-...",
  "method": "BANK_TRANSFER",
  "status": "WAITING_DEPOSIT",
  "amount": 180000,
  "bankName": "KB국민은행",
  "accountNumber": "1111-2222-3333-4444",
  "depositorName": "홍길동",
  "requestedAt": "2026-07-27 12:00:00",
  "paidAt": null,
  "expiresAt": "2026-07-27 23:59:59"
}
```

주요 실패:

```text
400 INVALID_REQUEST     은행 코드 불일치, 결제 상태 부적합
403 FORBIDDEN           다른 사용자의 결제 요청
```

## 가상계좌 입금 시뮬레이션

```http
POST /api/v1/payments/virtual-account/deposit
Authorization: Bearer {adminAccessToken}
Content-Type: application/json
```

이 API는 사용자가 직접 누르는 API가 아니라 은행 입금 통지를 흉내 내는 테스트용 API다. 따라서 `ADMIN` 또는 local/test 전용으로 제한한다. `X-Active-Token`은 받지 않는다.

요청 DTO 후보: `VirtualAccountDepositRequest`

```json
{
  "accountNumber": "1111-2222-3333-4444",
  "amount": 180000
}
```

처리 규칙:

```text
1. accountNumber로 Payment 조회
2. payment.status == WAITING_DEPOSIT 검증
3. expiresAt 만료 여부 검증
4. amount 일치 검증
5. 성공하면 공통 결제 완료 처리 호출
```

성공 응답: `PaymentResponse`

```json
{
  "paymentId": 1,
  "reservationId": 1,
  "orderId": "ORDER-...",
  "paymentNo": "PAY-...",
  "method": "BANK_TRANSFER",
  "status": "PAID",
  "amount": 180000,
  "bankName": "KB국민은행",
  "accountNumber": "1111-2222-3333-4444",
  "depositorName": "홍길동",
  "requestedAt": "2026-07-27 12:00:00",
  "paidAt": "2026-07-27 12:10:00",
  "expiresAt": "2026-07-27 23:59:59"
}
```

주요 실패:

```text
400 INVALID_REQUEST 계좌번호 불일치, 금액 불일치, 이미 만료된 입금, 결제 상태 부적합
403 FORBIDDEN       관리자 권한 없음
```

## 보안 matcher

`ticket-service`의 `SecurityConfig`에는 다음 matcher를 추가한다.

```java
.requestMatchers("/api/*/payments/card/**").hasAnyRole("USER", "ADMIN")
.requestMatchers("/api/*/payments/virtual-account/issue").hasAnyRole("USER", "ADMIN")
.requestMatchers("/api/*/payments/virtual-account/deposit").hasRole("ADMIN")
```

## DTO 목록

```text
CardPaymentApproveRequest
- paymentNo: String, required
- cardCompany: String, required
- cardNumber: String, required
- cvc: String, required
- cardPassword: String, required

VirtualAccountIssueRequest
- paymentNo: String, required
- bankCode: String, required
- depositorName: String, required

VirtualAccountDepositRequest
- accountNumber: String, required
- amount: Integer, required, positive

PaymentResponse
- 기존 DTO 재사용
```
