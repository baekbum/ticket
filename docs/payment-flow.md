# 결제 진행 플로우

이 문서는 현재 구현 기준으로 카드 결제와 무통장 입금 결제의 서비스 간 흐름을 정리한다.

## 공통 전제

- 사용자는 좌석 점유 이후 배송/쿠폰/결제수단 입력 화면으로 진입한다.
- `CheckoutController.prepare`는 대기열 active token과 좌석 점유 상태를 검증한다.
- `CheckoutController.confirm`은 예약, 배송, 결제 기본 정보를 생성한다.
- 카드 승인, 가상계좌 발급, 입금 처리는 `payment-gateway-service`가 담당한다.
- `ticket-service`는 gateway의 내부 요청 또는 Kafka 이벤트를 받아 최종 결제 상태를 반영한다.

## 카드 결제

카드 소유자와 예매자는 분리한다. 승인 요청에서 명의자 이름을 전달하거나 비교하지 않고, 카드사·카드번호로 조회한 카드의 CVC·비밀번호·만료·한도를 검증한다. 결제 이력과 ticket 완료 요청에는 로그인한 예매자 ID를 사용하며, 취소/환불은 실제 승인에 사용한 카드에 반영한다.

### 정상 처리 순서

```text
1. 브라우저 -> checkout/prepare, checkout/confirm
   서버 금액 계산, 예약/배송/Payment 생성, READY와 paymentNo 반환
2. 브라우저 -> gateway 카드 승인 API (카드 입력 화면에서 호출)
3. GatewayCardApprovalService
   카드 검증 -> ticket 내부 /card/validate 요청
   ticket이 소유자/카드 결제수단/READY/만료 검증 후 저장된 금액 반환
   요청 금액 일치 확인 후 서버 금액으로 카드 한도 차감 + APPROVED 이력 저장 및 커밋
4. GatewayCardSettlementService -> ticket 내부 /card/settle
   같은 paymentNo + transactionId로 결제 완료 요청
5. ticket
   결제 row 잠금, 소유자/금액/상태/만료/기존 거래번호 검증
   PAID 및 예약/티켓/좌석 확정 후 COMPLETED 응답
6. gateway
   TICKET_PAYMENT_COMPLETED 저장 및 커밋
7. 브라우저
   HTTP 200 승인 성공 응답 후 결제 완료 화면 이동
```

승인과 완료 반영은 별도 Spring 서비스의 트랜잭션이다. 오케스트레이터에는 트랜잭션을 두지 않는다.
카드 입력 오류나 사전 검증 실패는 로컬 트랜잭션을 롤백한다. 신규 승인 이력을 만들거나 ticket을 FAILED로 변경하지 않으므로 READY 결제번호로 입력을 고쳐 재시도할 수 있다.

### 응답 유실과 복구

- ticket 호출 타임아웃/5xx/응답 불일치, gateway 완료 기록 커밋 실패는 즉시 승인 취소하지 않는다.
- gateway는 승인 이력을 유지하고 HTTP 202 `PENDING`을 반환한다. 브라우저는 결제 완료 화면으로 이동하거나 새 결제를 시작하지 않고 상태를 조회한다.
- gateway가 재처리 상태를 저장하지 못해도 이미 커밋된 APPROVED 이력이 복구 대상이다.
- 자동 복구는 기본 30초마다 APPROVED/TICKET_PAYMENT_FAILED를 오래된 순으로 최대 100건 조회한다. 각 건의 예외는 다음 건 처리를 막지 않는다.
- 동일 결제번호 재요청과 스케줄러는 같은 승인 거래번호로 /card/settle을 재호출한다. 이미 PAID이면 거래번호가 같은 경우에만 완료를 재응답한다.
- gateway의 완료 표시 저장만 실패했다면 ticket을 원복하지 않고 해당 표시를 복구한다.
- HTTP 202 이후 `GET /payment-gateway/api/v1/payments/card/{paymentNo}`를 조회한다. TICKET_PAYMENT_COMPLETED면 완료 화면, CANCELLED면 승인 취소 안내를 표시한다. APPROVED/TICKET_PAYMENT_FAILED는 확인 대기다.

### 명확한 완료 거부

- /card/settle은 EXPIRED/CANCELLED/FAILED처럼 더 이상 완료될 수 없는 상태에만 REJECTED를 반환한다.
- READY지만 기한이 지났으면 ticket의 만료 및 예매 정리를 커밋한 뒤 REJECTED를 반환한다.
- gateway는 REJECTED 응답의 결제번호/금액/상태를 확인한 뒤 카드 사용액 복구와 CANCELLED 기록을 같은 트랜잭션에서 커밋한다.
- 취소 커밋 실패도 기존 승인 이력을 통해 재처리한다. HTTP 오류 코드만으로 취소를 결정하지 않는다.

### 상태 흐름

```text
ticket: READY -> PAID 또는 EXPIRED/FAILED/CANCELLED
gateway: APPROVED -> TICKET_PAYMENT_COMPLETED
         APPROVED -> TICKET_PAYMENT_FAILED -> 재처리 -> TICKET_PAYMENT_COMPLETED 또는 CANCELLED
```

paymentNo unique 제약은 중복 승인 저장을 차단한다. 완료/취소/환불은 승인 이력 잠금으로 직렬화하고 카드 사용액 변경에도 카드 row 잠금을 사용한다. 동시 최초 요청 중 unique 충돌이 난 요청은 같은 paymentNo의 상태를 확인한 뒤 재시도한다.

자동 복구가 계속 실패하는 건은 gateway 이력과 경고 로그로 확인한다. 데이터 불일치나 서비스 인증 설정 오류는 원인을 수정해야 하며, 재시도만으로 해결된다고 가정하지 않는다.

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

8. payment-gateway-service -> ticket-service
   입금 완료 내부 API 호출
   ticket-service 결제 완료 반영 요청

9. ticket-service
   Payment.status = PAID
   예약/티켓/좌석 상태 확정

10. payment-gateway-service
    ticket-service 반영 성공 시 DummyVirtualAccount.status = TICKET_PAYMENT_COMPLETED
    ticket-service 반영 실패 시 DummyVirtualAccount.status = TICKET_PAYMENT_FAILED
    실패 사유 저장
```

### 상태 흐름

```text
ticket Payment: READY -> WAITING_DEPOSIT -> PAID
gateway virtual account: WAITING_DEPOSIT -> DEPOSITED -> TICKET_PAYMENT_COMPLETED 또는 TICKET_PAYMENT_FAILED
```

### 입금 만료 기준

- 공연 당일에는 무통장 입금 결제를 사용할 수 없다.
- 공연 전날이면 발급 당일 `23:59:59`까지 입금 가능하다.
- 그 외에는 다음날 `23:59:59`까지 입금 가능하다.

## 구매 제한 카운트 기준

- `CheckoutController.confirm` 시점에는 실제 결제 완료가 아니므로 구매 제한 카운트를 증가시키면 안 된다.
- 카드 결제는 gateway 승인 성공 후 ticket-service가 `PAID`로 반영할 때 증가시키는 것이 맞다.
- 무통장 입금은 gateway의 입금 완료 내부 API 요청을 받아 ticket-service가 `PAID`로 반영할 때 증가시키는 것이 맞다.
- 즉, 구매 제한 카운트 증가는 결제 성공 확정 처리와 같은 트랜잭션 흐름에 두는 것이 안전하다.

## 현재 남은 정리 포인트

- 결제 수단 변경 시 기존 `READY` 결제 건 처리 정책 확정
- payment-gateway 관리자 재처리 API 추가
  - `TICKET_PAYMENT_FAILED` 상태의 가상계좌 입금 완료 건을 운영자가 확인 후 재반영
  - ticket-service 내부 API 재호출
  - 성공 시 `TICKET_PAYMENT_COMPLETED`, 실패 시 실패 사유 갱신
  - 실제 운영/정산 기능 범위에 가까우므로 현재 티켓팅 핵심 플로우에서는 보류
