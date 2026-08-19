# 결제 진행 플로우

이 문서는 현재 구현 기준으로 카드 결제와 무통장 입금 결제의 서비스 간 흐름을 정리한다.

## 공통 전제

- 사용자는 좌석 점유 이후 배송/쿠폰/결제수단 입력 화면으로 진입한다.
- `CheckoutController.prepare`는 대기열 active token과 좌석 점유 상태를 검증한다.
- `CheckoutController.confirm`은 예약, 배송, 결제 기본 정보를 생성한다.
- 카드 승인, 가상계좌 발급, 입금 처리는 `payment-gateway-service`가 담당한다.
- `ticket-service`는 gateway의 내부 요청 또는 Kafka 이벤트를 받아 최종 결제 상태를 반영한다.

## 카드 결제

### 처리 순서

```text
1. 프론트 -> ticket-service
   CheckoutController.prepare 호출
   대기열 active token과 좌석 점유 상태 검증

2. 프론트 -> ticket-service
   CheckoutController.confirm 호출
   예약/배송/Payment 생성
   Payment.status = READY
   paymentNo 반환

3. 프론트
   confirm 응답의 paymentNo, amount를 기준으로 카드 결제 팝업 표시

4. 프론트 -> payment-gateway-service
   카드 승인 API 호출
   paymentNo, 카드사, 카드번호, CVC, 카드 비밀번호, 금액 전달

5. payment-gateway-service
   더미 카드 정보 검증
   카드 한도 차감
   카드 결제 이력 APPROVED 저장

6. payment-gateway-service -> ticket-service
   completeCardFromGateway 내부 API 호출

7. ticket-service
   Payment.status = PAID
   예약/티켓/좌석 상태 확정

8. 프론트
   payment-gateway 승인 성공 응답을 받으면 결제 완료 페이지로 이동
```

### 상태 흐름

```text
ticket Payment: READY -> PAID
gateway card history: APPROVED -> TICKET_PAYMENT_COMPLETED 또는 TICKET_PAYMENT_FAILED
```

### 실패 기준

- `confirm` 성공 후 카드 승인이 실패하면 `ticket-service`의 `Payment.status`는 `READY`로 남는다.
- 사용자는 카드 결제를 다시 시도하거나 다른 결제 수단을 선택할 수 있다.
- 카드 승인 성공 후 ticket-service 반영에 실패하면 gateway 카드 이력은 실패 상태로 남긴다.

## 무통장 입금

### 처리 순서

```text
1. 프론트 -> ticket-service
   CheckoutController.prepare 호출
   대기열 active token과 좌석 점유 상태 검증

2. 프론트 -> ticket-service
   CheckoutController.confirm 호출
   예약/배송/Payment 생성
   Payment.status = READY

3. ticket-service -> payment-gateway-service
   가상계좌 발급 API 호출
   paymentNo, 은행사, 금액, 공연일시 전달

4. payment-gateway-service
   공연 당일 무통장 입금 불가 검증
   은행사 고유 번호와 랜덤 값으로 가상계좌번호 생성
   입금 만료 시각 계산
   DummyVirtualAccount.status = WAITING_DEPOSIT
   발급 이력 저장

5. ticket-service
   gateway 응답의 은행명, 계좌번호, 만료일을 Payment에 반영
   Payment.status = WAITING_DEPOSIT
   프론트에 계좌 정보 반환

6. 프론트
   예매 완료 화면에 입금 계좌, 금액, 만료 시각 표시
   이 시점은 결제 완료가 아니라 입금 대기 상태

7. 입금 발생 -> payment-gateway-service
   무통장 입금 API 호출
   계좌번호, 입금자명, 금액 검증
   DummyVirtualAccount.status = DEPOSITED
   입금 이력 저장

8. payment-gateway-service scheduler
   DEPOSITED 상태의 가상계좌 조회
   Kafka 입금 완료 이벤트 발행
   DummyVirtualAccount.status = DEPOSIT_EVENT_PUBLISHED

9. ticket-service Kafka consumer
   입금 완료 이벤트 수신
   Payment.status = PAID
   예약/티켓/좌석 상태 확정
```

### 상태 흐름

```text
ticket Payment: READY -> WAITING_DEPOSIT -> PAID
gateway virtual account: WAITING_DEPOSIT -> DEPOSITED -> DEPOSIT_EVENT_PUBLISHED
```

### 입금 만료 기준

- 공연 당일에는 무통장 입금 결제를 사용할 수 없다.
- 공연 전날이면 발급 당일 `23:59:59`까지 입금 가능하다.
- 그 외에는 다음날 `23:59:59`까지 입금 가능하다.

## 구매 제한 카운트 기준

- `CheckoutController.confirm` 시점에는 실제 결제 완료가 아니므로 구매 제한 카운트를 증가시키면 안 된다.
- 카드 결제는 gateway 승인 성공 후 ticket-service가 `PAID`로 반영할 때 증가시키는 것이 맞다.
- 무통장 입금은 입금 완료 Kafka 이벤트를 받아 ticket-service가 `PAID`로 반영할 때 증가시키는 것이 맞다.
- 즉, 구매 제한 카운트 증가는 결제 성공 확정 처리와 같은 트랜잭션 흐름에 두는 것이 안전하다.

## 현재 남은 정리 포인트

- 카드 승인 실패, 결제 만료, 결제 수단 변경 시 기존 `READY` 결제 건 처리 정책 확정
