# 결제 API 계약

이 문서는 사용자 결제 시나리오 구현 전에 고정할 API 계약이다. 실제 PG 대신 백엔드가 카드 승인과 무통장 입금을 시뮬레이션한다.

## 공통 규칙

사용자 결제 API는 로그인 사용자의 예약에 대해서만 동작해야 한다.

```text
Authorization: Bearer {accessToken}
X-Queue-Token: {queueToken}
Content-Type: application/json
```

`X-Queue-Token`은 좌석 점유, 체크아웃 준비와 동일한 active token이다. 카드 승인과 가상계좌 발급 단계에서 TTL을 검증한다.

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
X-Queue-Token: {queueToken}
Content-Type: application/json
```

요청 DTO 후보: `CardPaymentApproveRequest`

```json
{
  "paymentNo": "PAY-...",
  "cardCompany": "KB",
  "cardNumber": "1234-5678-9012-3456",
  "cvc": "123",
  "cardPassword": "12"
}
```

처리 규칙:

```text
1. X-Queue-Token TTL 검증
2. paymentNo로 Payment 조회
3. payment.reservation.userId와 현재 로그인 사용자 일치 검증
4. payment.status == READY 검증
5. 더미 카드 정보 검증
6. 성공하면 공통 결제 완료 처리 호출
7. 실패하면 Payment 상태는 READY로 유지
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
403 QUEUE_ACCESS_DENIED active token 누락, 만료, 불일치
404 INTERNAL_SERVER_ERROR 또는 INVALID_REQUEST 현재 결제 조회 실패 처리 기준에 맞춤
```

## 가상계좌 발급

```http
POST /api/v1/payments/virtual-account/issue
Authorization: Bearer {accessToken}
X-Queue-Token: {queueToken}
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
1. X-Queue-Token TTL 검증
2. paymentNo로 Payment 조회
3. payment.reservation.userId와 현재 로그인 사용자 일치 검증
4. payment.status == READY 검증
5. bankCode로 은행명과 계좌 prefix 결정
6. 랜덤 계좌번호 생성
7. Payment.bankName, accountNumber, depositorName, expiresAt 저장
8. Payment.status = WAITING_DEPOSIT
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
  "bankName": "KB",
  "accountNumber": "1111-2222-3333-4444",
  "depositorName": "홍길동",
  "requestedAt": "2026-07-27 12:00:00",
  "paidAt": null,
  "expiresAt": "2026-07-27 12:30:00"
}
```

주요 실패:

```text
400 INVALID_REQUEST     은행 코드 불일치, 결제 상태 부적합
403 FORBIDDEN           다른 사용자의 결제 요청
403 QUEUE_ACCESS_DENIED active token 누락, 만료, 불일치
```

## 가상계좌 입금 시뮬레이션

```http
POST /api/v1/payments/virtual-account/deposit
Authorization: Bearer {adminAccessToken}
Content-Type: application/json
```

이 API는 사용자가 직접 누르는 API가 아니라 은행 입금 통지를 흉내 내는 테스트용 API다. 따라서 `ADMIN` 또는 local/test 전용으로 제한한다. `X-Queue-Token`은 받지 않는다.

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
  "bankName": "KB",
  "accountNumber": "1111-2222-3333-4444",
  "depositorName": "홍길동",
  "requestedAt": "2026-07-27 12:00:00",
  "paidAt": "2026-07-27 12:10:00",
  "expiresAt": "2026-07-27 12:30:00"
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

기존 `POST /api/v1/payments/confirm`은 프론트용 공개 API로 확장하지 않는다. 다음 단계에서 공통 완료 처리 서비스 메서드로 내리거나, 임시 테스트 API로 유지할지 결정한다.

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
