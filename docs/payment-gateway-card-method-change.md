# 신규 PG 카드의 무통장 전환과 승인 결과 복구

2026-11-16 카드 승인 실패·응답 유실 이후 동일 checkout 요청에서 카드→무통장 전환을 지원한다. 브라우저의 실패 응답이나 단순 승인 조회의 404를 미승인 확정으로 사용하지 않는다.

## 처리 흐름

```text
브라우저 → Ticksy /checkout/confirm (같은 checkout 키, BANK_TRANSFER)
  [Ticksy TX] 사용자·주문·좌석 검증 → 최초 전환 은행·입금기한 저장·커밋
  기존 PG ID가 없으면 최초 카드 조건으로 prepare → ID 바인딩·커밋
Ticksy → PG /gateway/{pgPaymentId}/method/bank-transfer
  [PG TX] 가맹점·원거래·금액 검증 → CHANGE_METHOD/REQUESTED 저장·커밋
PG → 카드사 /cards/gateway/close-approval
  [카드 TX] 같은 원거래의 승인 차단 행을 잠금
  ├─ 승인 존재 → 기존 원승인 반환
  │  [PG TX] PAID·승인 이력·기존 통보 Outbox 복구
  │  Ticksy는 같은 승인 사실로 예매·티켓·Payment 완료, 계좌 발급 없음
  └─ 승인 없음 → 이후 승인도 영구 차단한 뒤 CLOSED 반환
     [PG TX] 같은 PG ID의 수단·은행·기한 변경, methodRevision 증가, 완료 이력
     [Ticksy TX] 원거래 응답 검증 → 무통장 수단과 발급 의도 반영·커밋
     기존 PG 계좌 발급 흐름으로 같은 ID의 은행 계좌 발급
브라우저 ← 기존 카드 성공 또는 무통장 발급 결과
```

결과가 불확실하면 실패를 반환하고 같은 요청을 다시 받는다. 전환 HTTP 자체를 자동 반복하는 스케줄러·Outbox는 추가하지 않는다. 계좌 발급은 기존 최대 4회 동기 재시도 정책을 유지한다.

## 경합과 트랜잭션

카드사의 card_approval_gates는 아직 승인 이력이 없는 PG 거래도 잠글 수 있게 한다. 카드 승인과 승인 차단은 같은 행을 PESSIMISTIC_WRITE로 잠가 직렬화한다.

- 승인이 먼저 잠금을 얻으면 한도 반영·원승인이 커밋된 뒤 차단 요청이 이를 읽는다. PG는 APPROVED를 반환하며 무통장 전환을 하지 않는다.
- 차단이 먼저 커밋되면 나중에 도착한 승인도 카드사에서 거절한다. PG의 사전 검증을 이미 통과했던 요청도 금융 실행 단계에서 차단된다.
- 차단 행은 OPEN→CLOSED만 가능하고 다시 열지 않는다. 동시 최초 생성은 별도 생성 트랜잭션의 INSERT 유니크 충돌을 복구한다. merge로 기존 CLOSED 행을 OPEN으로 덮어쓰지 않는다.
- 생성용 REQUIRES_NEW는 잠글 행을 먼저 커밋하기 위한 경계다. 승인·차단의 실제 금융 상태 변경은 각 금융 요청의 트랜잭션에서 처리한다. 초기 OPEN 행이 남아도 승인 성공을 의미하지 않는다.
- PG는 금융 HTTP 동안 원거래 락을 유지하지 않는다. 전환 의도와 최종 결과는 HTTP 전후의 짧은 로컬 트랜잭션으로 저장한다.

PG에서 전환 의도가 기록된 뒤에는 새 카드 승인 실행을 거절하고 새 세션도 조회 전용으로 발급한다. 전환 완료 시 methodRevision을 올리고 저장된 수단도 BANK_TRANSFER로 변경하므로 이전 카드 세션은 유효하지 않다. 최종 보호는 카드사 차단 행이며 세션 무효화만으로 경합을 해결했다고 가정하지 않는다.

전환 중 금융 승인 조회가 비어 있으면 409로 전환 확인을 안내한다. 브라우저가 이를 승인 없음으로 해석해 다시 승인하지 않는다. 다른 창에서 전환이 완료된 기존 카드 화면은 GATEWAY_PAYMENT_METHOD_CHANGED를 받고 조회를 종료한다.

## 식별자와 최초 조건

- pgPaymentId, Ticksy paymentNo, checkout 멱등키를 모두 유지한다. 전환을 위해 새 PG 결제나 매핑 테이블을 생성하지 않는다.
- CheckoutAttempt.gateway_switch_bank_company·gateway_switch_expires_at에 최초 전환 조건을 고정한다. 날짜가 바뀐 재요청도 최초 입금기한을 사용하며 다른 은행 요청은 거절한다.
- PG는 CHANGE_METHOD의 REQUESTED·COMPLETED·APPROVAL_FOUND 작업 키로 최초 조건과 결과를 구분한다. 다른 금액·구매자·은행·입금기한은 같은 전환으로 재응답하지 않는다.
- 최초 prepare의 creation_request_hash는 유지한다. 전환은 명시적 API로만 실행하며 변경된 은행 조건으로 같은 결제번호의 prepare를 다시 보내는 기능은 아니다. 전환 후 발급은 저장된 PG ID를 사용한다.
- 공연 당일 무통장 제한과 입금기한 계산은 Ticksy가 담당한다. PG·은행에는 일반 은행 코드·기한만 전달한다.
- 최초 전환은 유효한 선점·대기열·판매 상태에서 요청해야 한다. 전환 응답을 복구하더라도 이미 만료·취소된 로컬 예매를 무통장 전환으로 되살리지 않는다. 기존 카드 승인 거절·보상 정책은 유지한다.

## API와 응답

가맹점 인증 POST `/api/v1/payments/gateway/{pgPaymentId}/method/bank-transfer`

```json
{"merchantId": 1, "buyerReference": "buyer", "amount": 10000,
 "bankCompany": "KB", "expiresAt": "2099-01-02T23:59:59"}
```

응답에는 outcome(SWITCHED/APPROVED), 원거래·가맹점·구매자·수단·금액·은행·기한·methodRevision을 포함한다. APPROVED에는 기존 Outbox와 같은 CardPaymentNotification 원승인 사실을 포함하며 SWITCHED에는 approval이 없다.

Ticksy는 승인 복구 응답의 내부 원승인도 현재 사용자·PG ID·결제번호·금액과 대조한 뒤 기존 멱등 완료 서비스를 호출한다. PG Outbox의 나중 중복 통보도 같은 원승인으로 수락한다. 이 동기 복구에서 브라우저가 PAID를 받는 시점에는 Ticksy의 로컬 완료가 커밋돼 있다.

결과 미확인은 503 GATEWAY_METHOD_CHANGE_UNCONFIRMED로 같은 요청 재시도를 안내한다. 응답 유실 시 CARD를 다시 승인하거나 다른 은행의 새 전환 요청으로 대체하지 않는다. 이미 PG가 전환됐지만 Ticksy 반영이 실패했으면 PG의 동일 결과로 복구한다. 승인 차단만 커밋되고 PG 확정이 실패했어도 늦은 카드 승인은 실행되지 않는다.

## 스키마와 호환성

- PG schema.sql: card_approval_gates 추가. PG ID PK, 최초 구매자·금액·카드 기한, 차단 여부·시각을 저장한다. 카드 원문은 저장하지 않는다.
- Ticksy schema.sql: checkout_attempts에 nullable 최초 전환 은행·기한 두 컬럼 추가. 별도 전환 매핑 엔티티는 만들지 않는다.
- 최종 schema.sql의 변경분을 실제 로컬 DB에 반영했다. 적용이 끝난 각 서비스의 card-method-change.sql은 삭제했다. checkout_attempts는 사용자 요청에 따라 스키마·데이터를 백업한 뒤 최신 schema.sql로 재생성했고, 데이터와 좌석 테이블의 외래키를 복원·검증했다. 작업 당시 checkout_attempts와 checkout_attempt_seats는 모두 0건이었다.
- 기존 거래에 차단 행을 무조건 생성하지 않는다. 최초 승인/차단 시 생성하고 기존 카드 원승인도 반드시 확인한다. Gate 미존재를 미승인으로 해석하지 않는다.
- PG 미연결 구형 카드의 기존 전환 경로는 유지한다. 신규 카드만 카드사 확정 차단 경로를 사용한다.

## 범위

카드→무통장 단방향 전환과 기존 카드 성공 복구를 지원한다. 차단 후 카드로 되돌리는 기능, 무통장 은행 변경, 미완료 전환의 자동 운영 조정은 이번 범위에 포함하지 않는다. 요청을 중단하면 최초 조건의 수동 재요청 또는 운영 확인이 필요하다. 예매 자체의 취소·환불은 기존 취소·환불 경로를 사용한다.

## 검증 결과

- 백엔드 회귀 테스트 741개 통과: common 45개, PG 390개, Ticksy 306개. 최종 실패·건너뜀은 없다. 새 의존성의 테스트 설정 누락으로 실패했던 checkout prepare 테스트 14개는 설정 보완 후 전부 재실행해 통과했다.
- 실제 PostgreSQL에서 승인과 차단의 동시 실행, 차단 행의 동시 최초 생성, 응답 유실 후 동일 요청 복구, 전환 저장 실패의 롤백, 사용자·가맹점 범위, 변경된 최초 조건 거절을 검증했다.
- 프런트엔드 테스트 41개, 프런트엔드 빌드, PG·Ticksy bootJar 빌드가 통과했다.
- 실행 중인 로컬 DB의 신규 테이블·컬럼을 확인했다. 브라우저에서 전체 결제 과정을 직접 조작하는 수동 검증은 수행하지 않았다.
