# PG 거래 기반 가상계좌 취소

2026-11-12 현재 Ticksy 신규 checkout은 prepare·PG ID 저장·신규 발급·취소 경로에 연결되어 있다. 만료도 [은행→PG Outbox와 Ticksy 조회](payment-gateway-account-expiration.md)로 연결했다. 아래 초기 단계의 미전환 범위 설명은 이 최신 내용으로 대체하며 신규 환불은 후속 작업이다.

2026-11-11 최신 구조: 입금은 BankController의 은행 업무 API다. POST /api/v1/banks/virtual-accounts/deposit에서 은행 상태·입금 이력·은행→PG Outbox를 함께 저장하고, PG는 수신 후 Ticksy에 별도 Outbox로 통보한다. Postman·Bruno도 같은 은행 API를 호출한다. 별도 테스트 컨트롤러와 support 입금 URL은 제거했다. 기존 PG 미연결 계좌는 POST /api/v1/banks/virtual-accounts/legacy/deposit를 사용한다. 인증은 기존 X-Payment-Provider-Token을 유지하며 PG→은행의 기존 내부 업무 URL과 금융 인증도 유지한다. [은행 API 상세](payment-gateway-bank-api.md)를 참고한다.

2026-11-10: 은행 취소 가능 여부는 은행이 판단한다. PG는 가맹점·원거래·요청 내용을 검증하고 Outbox로 요청을 전달하며, 확정 결과를 기록하고 가맹점에 별도 Outbox로 통보한다. 취소 경로는 입금 처리 서비스나 입금 Outbox를 생성하지 않는다.

## API와 호환성

POST /api/v1/payments/virtual-account/gateway/{pgPaymentId}/cancel

인증은 기존 가맹점 서비스 토큰이다. merchantId는 선택 교차 검증값이며 인증을 대신하지 않는다. 요청은 buyerReference, amount, bankCompany, accountNumber를 포함한다. PG는 인증 가맹점의 거래, 저장된 BANK_TRANSFER, 예매자·금액·은행·발급 계좌를 검증한다. 다른 가맹점과 없는 거래는 동일한 404다. 기존 거래 정리는 가맹점 사용 중단 후에도 허용한다.

최초 접수와 처리 중 중복 요청은 HTTP 202다. 아직 은행을 호출하거나 취소 완료로 표시하지 않는다.

~~~json
{
  "pgPaymentId": "20261110550e8400-e29b-41d4-a716-446655440000",
  "paymentNo": "PAY-1",
  "eventId": "20261110550e8400-e29b-41d4-a716-446655440000:CANCEL:INITIAL",
  "status": "PROCESSING",
  "pgStatus": "WAITING_DEPOSIT",
  "fundsReturned": false
}
~~~

은행 결과 확정 후 동일 요청은 HTTP 200으로 최초 CANCELLED 또는 REJECTED 결과와 reason을 반환한다. 성공이면 cancelledAt이 포함된다. GET /api/v1/payments/virtual-account/gateway/{pgPaymentId}/cancellation은 같은 가맹점 인증과 소유 범위로 처리 상태를 조회한다. 접수 전이면 404다.

기존 PG ID 취소 API의 동기 200 완료 계약은 이 비동기 접수 계약으로 바뀐다. 기존 paymentNo 기반 /virtual-account/cancel과 Ticksy의 현재 취소 Outbox 호출은 유지한다. 실제 checkout의 prepare·pgPaymentId 저장·새 호출 전환은 후속 작업이다. 기존 예매·티켓·Payment 취소, 좌석 AVAILABLE 복원, Ticksy 취소 Outbox의 공동 커밋은 변경하지 않는다.

## 처리와 트랜잭션

1. PG 접수: 원거래 잠금 안에서 불변 요청을 검증하고 BANK_CANCEL_REQUEST 행을 저장한다. 커밋 후 접수 응답을 반환한다.
2. PG 워커: 60초 임대를 짧게 커밋한 뒤 락 없이 은행 HTTP /internal/financial-simulations/banks/virtual-accounts/gateway/cancel을 호출한다.
3. 은행: 입금과 같은 계좌 행을 잠근다. 최초 취소 요청·확정 결과를 bank_virtual_account_cancellations에 계좌 변경·은행 이력과 함께 커밋한다. HTTP 200의 구조화한 결과는 CANCELLED 또는 REJECTED다.
4. PG 확정: 원거래와 현재 임대를 확인하고 PG 결과·취소 성공/거절 이력·요청 Outbox DELIVERED·MERCHANT_CANCEL_RESULT Outbox를 같은 PG 트랜잭션에 저장한다.
5. 가맹점 전달: 등록된 merchant_notification_endpoints의 URL·복호화 토큰으로 최종 결과를 전달한다. 가맹점이 결과 수신을 커밋하고 정확한 확인 응답을 보내면 결과 Outbox만 DELIVERED로 바꾼다.

은행→PG 취소 결과 전용 Outbox는 없다. 은행의 최종 HTTP 응답과 수신 측 멱등 결과 저장으로 응답 유실을 복구한다. HTTP 202·빈 본문·잘못된 원거래·형식 오류·5xx는 최종 확인으로 인정하지 않는다.

## 업무 결과와 전달 상태

은행은 대기/만료 계좌를 취소할 수 있다. 이미 취소된 원계좌는 성공이다. 입금 완료 계좌의 신규 입금 전 취소는 ALREADY_DEPOSITED로 거절하며 실제 반환이나 환불을 하지 않는다. 미발급 계좌도 ACCOUNT_NOT_FOUND라는 확정 거절을 저장한다. 금융 입력과 원계좌가 다르면 거절한다.

REJECTED도 은행이 확정한 결과이므로 은행 요청 Outbox는 DELIVERED다. PG 금융 상태를 실패/취소로 바꾸지 않고 CANCEL/REJECTED_INITIAL 이력을 남기며, 가맹점에 거절 결과를 전달한다. 은행 성공이면 CANCEL/INITIAL 이력을 남긴다. 가맹점 통보 실패는 금융 취소를 되돌리지 않는다.

## 멱등성과 응답 유실

PG 접수의 키는 pgPaymentId + CANCEL + INITIAL이다. 같은 가맹점의 원거래 잠금과 Outbox UNIQUE(flow, pg_payment_id), UNIQUE(flow, event_id)가 동시 접수를 직렬화한다. 다른 가맹점의 같은 paymentNo는 다른 PG ID이므로 충돌하지 않는다. 금액 표현 100/100.00은 같게 정규화하며 같은 키의 다른 소유자·은행·계좌·금액은 거절한다.

은행은 성공과 업무 거절 모두 최초 요청/결과를 고정한다. 이후 계좌 상태가 바뀌어도 동일 키 재시도는 최초 결과를 반환한다. 은행 커밋 후 응답 유실 또는 PG 저장 실패는 같은 은행 요청을 다시 보내 복구한다. PG 확정 트랜잭션 실패는 PG 상태·이력·DELIVERED·다음 Outbox가 모두 롤백되므로 일부만 완료되지 않는다.

각 워커는 HTTP 중 금융/PG/Outbox 락을 보유하지 않는다. 실패 후 30초 재시도, 종료 시 60초 임대 만료 후 회수한다. attempt가 바뀐 이전 워커의 지연 완료/실패는 무시한다. 토큰·HTTP 원문은 Outbox나 오류에 저장하지 않는다.

## 입금과 취소의 경합

은행의 계좌 잠금이 순서를 결정한다. 취소가 먼저 커밋되면 신규 입금은 거절한다. 입금이 먼저면 신규 취소를 확정 거절하며, PG가 입금 통보를 아직 받지 못했더라도 은행이 판단한다. 이미 취소된 계좌에 과거 입금 사실이 있는 결과는 취소 스냅샷에만 보존한다. 취소 경로에서 deposits.confirm을 호출하지 않는다.

은행의 독립 입금 Outbox가 늦게 도착하면 PG는 최초 입금 시각·이력·가맹점 입금 통보를 한 번 기록하되 CANCELLED를 PAID로 되돌리지 않는다. 기존 PG paidAt도 취소 때문에 지우지 않는다. Ticksy RESERVATION_CANCELLED 입금 응답의 은행 계좌 정리·PG 취소 수렴·입금 통보 완료 경로는 유지한다. 실제 자금 반환은 없으며 fundsReturned=false다.

## 가맹점 결과 수신

기존 등록 통보 URL과 PAYMENT_NOTIFICATION_V1을 재사용한다. X-PG-Event-Type: BANK_CANCELLATION 헤더로 카드/입금 DTO와 구분한다. BankCancellationNotification은 버전·이벤트·가맹점·PG 거래·가맹점 번호·소유자·수단·금액·계좌·은행·성공/거절·사유·취소 시각·fundsReturned를 고정한다. 응답은 BankCancellationNotificationResponse로 같은 이벤트/거래/가맹점/금액/결과를 확인한다.

Ticksy는 가맹점 코드와 원결제 소유자·수단·금액·계좌·은행을 확인하고 gateway_bank_cancellation_receipts에 최초 통보를 커밋한 후 응답한다. 같은 키의 다른 내용은 거절한다. 성공 통보는 이미 취소된 로컬 Payment를 확인하며 예매·좌석·티켓 취소를 재실행하지 않는다. 은행 거절은 로컬 예매를 되살리지 않고 수신 이력에 보존한다. 거절에 대한 운영 조정이나 실제 자금 반환은 자동 구현하지 않는다.

## 스키마와 기존 자료

bank_transfer_outbox는 BANK_DEPOSIT / BANK_CANCEL_REQUEST / MERCHANT_CANCEL_RESULT의 세 논리 전달 단계를 공통 임대 엔진으로 저장한다. 단계마다 별도 행·상태·결과·재시도이며, 은행 입금과 가맹점 입금의 기존 gateway_bank_notification_outbox도 별개다. 성공/거절 멱등 결과용 은행 테이블과 Ticksy 수신 이력 테이블도 추가했다.

최신 테이블 정의는 각 서비스의 schema.sql에 유지한다. 현재 DB에 반영됐음을 대조한 뒤 일회성 add 마이그레이션 파일은 삭제했다. 기존 테이블·데이터 삭제나 초기화는 하지 않는다. 기존 PG 연결 입금 중 은행 Outbox가 없는 행은 은행 워커가 최초 사실 그대로 편입한다. 기존 완료 가맹점 Outbox를 PENDING으로 되돌리지 않으며 재전달 입금은 PG 멱등 이력으로 확인한다. 가맹점 식별 불가한 NULL 연결 금융 자료는 기존 전용 경로에 남긴다.

## 최종 검증 (2026-11-11)

동일 회귀 명령의 최종 실행이 BUILD SUCCESSFUL로 완료됐다. 공통 45개, PG 311개, Ticksy 130개로 총 486개가 통과했고 실패·오류는 0개다. 선택 환경 변수 CHECKOUT_TEST_POSTGRES_URL이 없는 기존 CheckoutPrepareRetryIntegrationTest 11개만 조건부 건너뜀이다. 실제 PostgreSQL의 PG 테스트 23개는 모두 실행해 통과했다.

검증에는 은행 입금/PG 통보 Outbox의 독립 완료, 가맹점별 동일 결제번호, 다른 가맹점 접근 차단, 같은 키의 다른 내용 거절, 중복·동시 취소, 입금/취소 경합, 은행·PG·가맹점 응답 유실, 은행 이력/Outbox/멱등 결과 저장 실패 롤백, PG 이력/다음 Outbox 저장 실패 롤백, 임대 회수·이전 워커 무시, 취소 후 지연 입금의 사실 보존, 표준 헤더/DTO의 실제 HTTP 전송, 가맹점 수신 이력과 재응답, 기존 카드·가상계좌·환불·예매·좌석·checkout·Feign 회귀를 포함한다.

실제 local-postgres/ticket DB에는 추가 전용 DDL로 3개 테이블을 생성했다. public 테이블은 58개에서 61개가 됐고, 기존 가맹점 1건과 결제 데이터는 유지됐다. 검증 후 PG/Ticksy 결제와 신규 Outbox/수신 이력은 모두 0건이다. 실제 DB 검증은 DDL validate와 롤백을 사용했고 실제 테이블 삭제·초기화는 수행하지 않았다. 빌드가 변경한 기존 Querydsl 13개 파일은 원본과 실질 내용이 같음을 확인한 후 EOF만 복원했으며 새 수신 이력 Q 클래스는 보존했다. git diff --check가 통과했다.

배포 시 추가 스키마와 Ticksy의 새 결과 수신 처리를 먼저 반영한 뒤 PG 워커를 배포한다. 기존 Ticksy checkout의 PG ID 저장·새 API 호출 전환과 PG 연결 만료/환불은 후속 범위다.
