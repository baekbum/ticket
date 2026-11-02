# 입금 전 예매 취소와 PG 계좌 취소

입금 전 무통장 예매는 전체 취소만 허용한다. 티켓 서비스는 예약을 먼저 잠그고 결제, 구매 매수, 티켓, 좌석 순서로 잠금을 획득한다. 예약·티켓·결제를 `CANCELLED`, 좌석을 `AVAILABLE`로 변경하고 `virtual_account_cancel_outbox`의 `PENDING` 작업을 같은 트랜잭션에 저장한다. 원래 트랜잭션이 롤백되거나 Outbox 저장이 실패하면 모두 롤백된다. 기존 입금 전 취소의 쿠폰 정책은 유지한다.

스케줄러는 커밋된 작업만 읽고 작업별 별도 트랜잭션에서 Outbox 행을 잠근 뒤 PG 취소 API를 호출한다. 원래 예약·결제 잠금을 잡은 채 HTTP를 호출하지 않는다. PG의 취소 상태와 결제번호·계좌·은행·금액을 모두 확인한 뒤 `DELIVERED`로 변경한다. HTTP 오류, 응답 유실, 불일치 응답은 `PENDING`과 재시도 정보를 유지한다. PG 성공 후 티켓의 완료 기록 커밋에 실패해도 같은 작업으로 재시도한다.

## PG 취소 API와 멱등성

`POST /api/v1/payments/virtual-account/cancel`은 유효한 `X-Service-Token`을 가진 내부 서비스만 호출할 수 있다. 요청은 `paymentNo`, `bankName`, `accountNumber`, `amount`를 포함한다. 응답은 동일한 식별 정보와 `status: CANCELLED`다.

취소 작업은 결제번호에 대한 전체 취소 한 종류이므로 Outbox의 `payment_no`에 고유 제약을 둔다. 별도 랜덤 멱등키는 만들지 않는다. PG는 입금과 동일한 계좌 행을 비관적 잠금으로 조회하고 계좌 정보를 검증한다. 이미 `CANCELLED`이면 성공을 재전달하며 취소 이력을 중복 생성하지 않는다. 대기·만료·입금 완료 상태도 티켓의 취소 요청으로 `CANCELLED`에 수렴시킨다.

포트폴리오 정책에 따라 늦게 입금된 돈을 실제로 반환하는 기능은 구현하지 않는다. `DEPOSITED`였던 계좌를 취소하더라도 입금자·입금 시각과 최초 입금 이력은 지우지 않고 별도 취소 이력을 기록한다.

## 입금·만료와 취소 경합

티켓의 입금·만료 통지도 예약 → 결제 순서로 잠근다. 취소 트랜잭션이 먼저 커밋했으면 입금은 예약·티켓·좌석을 변경하지 않는다. 내부 입금 완료 API는 HTTP 200과 다음 업무 결과를 반환한다.

```json
{
  "result": "RESERVATION_CANCELLED",
  "payment": { "paymentNo": "PAY-...", "status": "CANCELLED" }
}
```

정상 입금 반영이나 이미 반영된 입금의 재전달에는 `result: APPLIED`와 기존 결제 응답을 반환한다. PG는 결과·결제 상태·계좌 식별 정보를 검증한다. `RESERVATION_CANCELLED`이면 계좌 취소와 입금 통지 Outbox의 `DELIVERED` 전환을 같은 PG 트랜잭션에서 커밋한다. 티켓 취소 Outbox와 PG 입금 통지 중 어느 쪽이 먼저 도착해도 취소 이력을 한 번만 기록한다.

취소된 결제에 늦게 도착한 계좌 만료 이벤트도 식별 정보를 검증한 뒤 상태 변경 없이 종료한다. 재판매된 좌석을 해제하지 않는다. 티켓의 입금 반영이 먼저 커밋되어 이미 `PAID`라면 기존 입금 후 취소·환불 정책을 따른다.

## 배포와 운영

기존 ticket DB에는 배포 전 `ticket-service/src/main/resources/sql/add-virtual-account-cancel-outbox.sql`만 적용한다. PG는 기존 테이블과 `CANCELLED` 상태·이력을 사용하므로 추가 테이블이 필요 없다. 내부 입금 API의 응답이 `result`와 `payment`를 포함하는 형태로 바뀌므로 PG와 티켓 서비스를 함께 배포한다. 배포 이전에 취소된 예매는 자동 소급 변경하지 않으며 별도 대사가 필요하다.

설정은 `app.virtual-account.cancel-outbox.fixed-delay-ms`, `retry-delay-ms`(기본 30000), `batch-size`(기본 100)다. 운영 시 `PENDING` 건수, 가장 오래된 생성 시각, 재시도 횟수, 오류 유형을 확인한다. 계좌·입금자 원문을 실패 로그에 남기지 않는다.

## 검증

`VirtualAccountCancelOutboxIntegrationTest`는 H2 PostgreSQL 모드의 격리 DB에서 취소 커밋·롤백, Outbox 저장 실패, 응답 유실과 재시도, 완료 커밋 실패, 동시 작업자, 취소와 자동·수동 입금 반영의 잠금 경합, 취소 후 재판매된 좌석의 입금·만료 보호를 검증한다. HTTP와 Redis 경계는 모의 구현을 사용한다.

PG의 `VirtualAccountDepositOutboxIntegrationTest`는 반복 취소, 입금 후 취소 응답, 두 취소 경로의 순서 역전, 응답 유실, 계좌 취소와 입금 통지 완료의 공동 롤백, 입금·취소 경합을 검증한다. `SecurityConfigAuthorizationTest`는 무인증·잘못된 내부 토큰·일반 사용자·입금 제공자의 취소 접근을 차단하고 내부 서비스만 허용하는지 확인한다.

```powershell
.\gradlew.bat :common:test :ticket-service:test :payment-gateway-service:test --continue --offline --no-daemon
```
