# PG 거래 기반 가상계좌 입금과 가맹점 통보

2026-11-09: 발급된 PG 연결 계좌의 입금·최초 금융 사실 복구·공통 가맹점 통보를 연결한다. 은행과 PG는 같은 앱/DB에 있으나 현재 선택한 RestClient HTTP 경계는 유지한다.

## 현재 흐름과 변경 범위

기존 공개 입금 API는 `POST /api/v1/payments/virtual-account/deposit`이며 `X-Payment-Provider-Token` 인증을 유지한다. 요청의 계좌번호에서 저장된 `pgPaymentId`를 찾는다. 가맹점 코드·결제수단·통보 URL을 요청 본문으로 선택하지 않는다.

~~~text
인증된 입금 제공자
 → GatewayVirtualAccountController
 → GatewayVirtualAccountDepositService (NEVER)
     → PG 원거래 수단·금액 검증
     → RestClient → BankController → BankVirtualAccountDepositService
         → 계좌 잠금 → 최초 입금 + 은행 이력 커밋
     → GatewayVirtualAccountDepositCommands
         → PG 거래 잠금 → PAID + DEPOSIT 이력 + 가맹점 통보 Outbox 커밋
 → GatewayBankNotificationDispatcher
     → 짧은 임대 트랜잭션 커밋
     → DB 등록 가맹점 URL로 공통 HTTP 통보
     → 별도 트랜잭션에서 전달 결과 확정
~~~

PG 연결이 없는 기존 계좌는 기존 금융 HTTP 경로·응답·입금 Outbox·Ticksy 계약을 유지한다. 신규 연결 계좌는 기존 Ticksy 전용 Outbox에 저장하지 않는다. 기존 paymentNo 단독 조회·취소·환불도 신규 연결 계좌에 접근하지 못한다.

신규 응답은 기존 은행 입금 필드에 `pgPaymentId`, `pgStatus`, `notificationStatus`를 추가하며 `paymentNo`에는 소유 가맹점의 결제번호를 반환한다. `status`는 은행 계좌 상태이며 PG 금융 상태·가맹점 통보 상태와 구분한다. 통보 성공을 기다리지 않고 입금 반영 결과를 반환한다.

이후 [응답 DTO 분리](payment-gateway-response-contract.md)에서 기존/신규 계좌의 자바 응답을 GatewayVirtualAccountPaymentDepositResponse로 통일했다. 기존 계좌는 PG 전용 세 필드를 JSON에서 제외해 이전 필드 계약을 유지하며, byte[] 또는 ResponseEntity<?>를 반환하지 않는다.

## 식별자·검증·멱등성

- 은행 계좌는 `pgPaymentId`를 불변으로 참조하고, 은행 내부 `payment_no`에도 PG ID를 사용한다. 서로 다른 가맹점의 같은 결제번호가 금융 테이블에서 충돌하지 않는다.
- 은행은 계좌 행을 비관적으로 잠그고 금액·입금자·입금 기한·계좌 상태를 확인한다. 이미 최초 입금 시각이 있으면 상태가 CANCELLED여도 기존 입금 사실로 같은 요청을 재응답한다.
- 같은 계좌의 최초 입금은 한 번만 기록한다. 다른 금액·입금자 또는 명시적으로 다른 시각을 보내면 409로 거절한다. 재요청 시 시각을 생략하면 최초 저장 시각을 재사용한다.
- PG는 저장된 BANK_TRANSFER 수단·금액·은행·기한·발급 계좌와 금융 응답을 대조한다. 다른 PG ID·계좌의 응답을 성공으로 반영하지 않는다.
- PG 이력은 `(pg_payment_id, DEPOSIT, INITIAL)`로 고정한다. 같은 이벤트 ID는 `{pgPaymentId}:DEPOSIT:INITIAL`이며 재시도마다 새 키를 만들지 않는다.
- 최초 PG 발급 확정이 유실된 READY 거래에 은행 입금이 확인되면 발급 이력과 입금 결과를 함께 복구한다. 기존 준비/발급 이력을 중복 생성하지 않는다.
- 은행은 Ticksy API·가맹점 인증·공연일 규칙을 참조하지 않는다. 발급 때 받은 일반적인 입금 기한을 사용한다.

## 트랜잭션과 실패 복구

| 경계 | 함께 커밋·롤백하는 항목 |
| --- | --- |
| 은행 입금 | 계좌의 최초 입금 반영 + 은행 입금 이력 |
| PG 입금 확정 | PG 상태·paidAt + DEPOSIT 이력 + PG→가맹점 Outbox |
| 취소 예매 결과 확정 | PG CANCELLED + 취소 이력 + Outbox 전달 완료 |

HTTP를 사이에 둔 은행/PG 트랜잭션 전체가 원자적이라고 가정하지 않는다. 은행 커밋 후 응답이 유실되거나 PG 이력/Outbox 저장이 실패하면 PG만 롤백될 수 있다. 같은 요청 재시도는 은행의 최초 입금을 재사용한다.

`GatewayVirtualAccountDepositService.recoverDeposits()`는 30초마다 최대 100개의 미확정 금융 입금을 찾고 은행 HTTP 조회로 원사실을 확인해 PG 결과를 복구한다. 직접 은행 입금 진입점이 호출되거나 프로세스가 중단된 경우도 동일하다. 확정 DEPOSIT 이력이 있으면 복구 후보에서 제외된다. 은행↔PG 내부 전달용 Outbox는 추가하지 않는다.

PG 조율 서비스와 통보 Dispatcher는 NEVER로 외부 HTTP가 기존 트랜잭션 안에 들어가는 것을 차단한다. 은행 입금과 PG 확정은 각각 짧은 REQUIRES_NEW 트랜잭션이다. 통보는 60초 임대 후 HTTP를 수행하며, 늦게 돌아온 이전 attempt의 결과는 새 결과를 덮어쓰지 못한다. 전달 실패는 30초 후 같은 이벤트로 재시도하고 금융 PAID를 실패로 변경하지 않는다.

## 공통 가맹점 수신 계약

같은 가맹점의 카드/은행 통보를 하나의 등록 URL로 수신하도록 공통 `GatewayPaymentNotification` 계약을 추가했다. JSON의 `paymentMethod`로 허용된 두 DTO만 선택하며 임의 클래스명은 해석하지 않는다. 컨트롤러는 요청/응답만 처리하고 수단별 서비스 선택은 수신 서비스가 담당한다.

- 표준 계약 코드: `PAYMENT_NOTIFICATION_V1`
- 호환 계약 코드: 기존 카드 Outbox의 `CARD_NOTIFICATION_V1`도 Resolver에서 허용한다.
- 새 Ticksy 공통 수신 API: `POST /api/v1/payments/internal/gateway/notifications`
- 기존 카드 전용 `/api/v1/payments/internal/gateway/card/notifications`와 기존 가상계좌 입금 완료 API는 유지한다.
- `BankDepositNotification`: 버전, 이벤트 ID, 가맹점 ID, PG ID, 가맹점 결제번호, 예매자, 수단, 금융 상태, 최초 금액·계좌·은행·입금자·시각.
- `BankDepositNotificationResponse`: 수신한 거래/가맹점/이벤트/계좌/금액을 확인하며 결과는 APPLIED 또는 RESERVATION_CANCELLED다. 통신 실패·잘못된 응답은 확정 거절로 처리하지 않는다.

Ticksy는 서버에 설정한 가맹점 ID를 확인하고 기존 입금 처리에 예매 소유자 검증을 추가해 사용한다. 중복·지연 통보에도 기존 결제·예매·티켓·좌석 상태 및 이력 규칙을 재사용한다. 결제수단/금액/계좌/은행/소유자가 다르면 반영하지 않는다.

URL과 암호화한 토큰은 기존 `merchant_notification_endpoints`에서 가맹점 ID + 대상 키로 조회한다. 신규 가맹점별 Java 구현체나 YAML 대상을 추가하지 않는다. 발급/입금 요청에서 임의 URL을 받지 않는다.

## 취소 예매의 입금 통보

Ticksy가 RESERVATION_CANCELLED를 반환하면 입금으로 예매·티켓·좌석을 되살리지 않는다. 은행 HTTP의 `reject-deposit`가 원계좌를 CANCELLED로 정리하고, PG는 CANCELLED·취소 이력·Outbox 완료를 함께 확정한다. 계좌 정리나 PG 저장 실패는 같은 거래/이벤트로 재시도한다.

이 경로는 실제 자금 반환이 아니다. 계좌와 PG의 최초 입금 시각·금액·입금 이력은 보존한다. PG 취소 이력에는 `fundsReturned=false`를 명시한다. 이미 정리된 동일 계좌의 재시도에도 새 입금이나 취소 이력을 추가하지 않는다.

## 스키마와 적용

`schema.sql`에 `gateway_bank_notification_outbox`를 추가한다. PG/소유 가맹점/최초 입금/계약/대상 키/이벤트/임대/재시도/완료 상태를 저장한다. PG 거래별 및 이벤트별 유니크, `(pg_payment_id, merchant_id)` 복합 FK로 소유권을 확인한다. 기존 테이블 삭제·초기화나 과거 금융 데이터 일괄 연결은 하지 않는다. 별도 마이그레이션 파일은 만들지 않는다.

Ticksy 등록 대상은 공통 API URL과 PAYMENT_NOTIFICATION_V1으로 전환한다. 기존 카드 이벤트의 식별자·payload·계약 스냅샷은 재발급하지 않는다. 공통 수신 API는 기존 카드 DTO도 그대로 수신한다. 배포 시 Ticksy 공통 수신 API를 먼저 준비한 뒤 등록 URL을 변경해야 한다.

## 남은 범위

가맹점의 PG 거래 기반 입금 전 취소 API와 만료 스케줄러 연동, 새 PG 거래의 환불, Ticksy/프런트의 prepare 및 pgPaymentId 저장 전환은 후속 작업이다. 기존 취소/입금 경합 회귀는 유지하고 이번에는 입금 통보에서 취소 예매가 확인된 경우의 수렴을 연결한다. 늦은 입금의 실제 자금 반환 기능은 구현하지 않는다.

## 검증 결과

2026-11-09 회귀 검증은 공통 모듈 45개, PG 257개, Ticksy 118개로 총 420개가 실패·오류·건너뜀 없이 통과했다. PG는 서비스·보안·설정·실제 PostgreSQL 모델을, Ticksy는 결제 서비스·checkout·Feign 설정·기존/신규 수신 API·실제 결제 모델을 포함한다.

이후 미확정 입금 대사 조회를 은행 저장소에서 PG 저장소로 이동하고 응답 message 호환성을 정리한 최종 코드에 대해 입금 통합 22개와 실제 PostgreSQL 19개, 총 41개를 다시 실행해 모두 통과했다. 실제 PostgreSQL에서는 미확정 입금의 native 대사 조회, 확정 후 제외, Outbox 매핑·임대·완료·복합 소유 FK를 검증했다. 실제 DB 테스트는 DDL validate와 롤백만 사용한다.

검증 범위에는 서로 다른 가맹점의 동일 결제번호·동일 대상 키, 중복/동시 입금, 다른 금액·입금자·시각, 잘못된 금융 응답, 은행 이력 저장 실패의 전체 금융 롤백, PG 이력/Outbox 저장 실패의 전체 PG 롤백, 은행·통보 응답 유실, 재시작 복구, 비활성 대상 재시도, 임대 만료/늦은 워커, 취소 예매 상태 수렴과 실제 자금 반환 없음이 포함된다. Ticksy 실제 결제 모델에서도 소유자 불일치 차단, 중복 이력 방지, 취소 예매 및 AVAILABLE 좌석 유지가 통과했다.

실제 local-postgres/ticket DB의 public 테이블은 58개다. 새 gateway_bank_notification_outbox와 두 전달 인덱스 및 유니크/소유 FK/상태 제약을 적용했다. Ticksy 1건은 PAYMENT_NOTIFICATION_V1과 공통 수신 URL로 갱신했고 암호화 토큰은 유지했다. 검증 후 PG 거래·신규 은행/카드 통보 Outbox는 0건이며 기존 테이블 삭제·초기화는 하지 않았다. 별도 마이그레이션 SQL은 생성하지 않았다. 빌드가 생성한 Querydsl 파일 13개의 끝 빈 줄만 원본과 대조해 정리했고 git diff --check가 통과했다.
