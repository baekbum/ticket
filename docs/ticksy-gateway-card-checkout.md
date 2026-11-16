# Ticksy 카드 결제의 PG 거래 연동

2026-11-15 신규 카드 checkout을 PG prepare → Ticksy PG ID 저장 → 브라우저 세션 승인 → 가맹점 통보로 연결했다.

## 실제 호출 경로

~~~text
브라우저 → Ticksy POST /api/v1/checkout/confirm
  [Ticksy TX] 예매·티켓·Payment·카드 준비 기한 저장
Ticksy → PG POST /api/v1/payments/gateway/prepare
  [PG TX] merchant + paymentNo로 준비·멱등 재응답
  [Ticksy TX] 최초 조건 확인 후 Payment.pgPaymentId 저장
브라우저 → Ticksy POST /api/v1/checkout/payments/{paymentNo}/gateway-session
Ticksy → PG POST /api/v1/payments/card/gateway/{pgPaymentId}/checkout-session
브라우저 ← 거래 전용 세션 (메모리에서만 보관)
브라우저 → PG POST /api/v1/payments/card/checkout/{pgPaymentId}/approve
  [사용자 JWT + X-PG-Payment-Session]
PG → 카드사 RestClient 승인
  [카드 TX] 카드 사용액·원승인 저장
  [PG TX] PAID·APPROVE 이력·가맹점 통보 Outbox 저장
PG → Ticksy 공통 카드 통보
  [Ticksy TX] PG ID·소유자·금액 확인 후 예매·티켓·Payment 완료
브라우저 → PG GET /api/v1/payments/card/checkout/{pgPaymentId}
  PG PAID + notificationStatus DELIVERED 확인 후 예매 완료 화면
~~~

외부 브라우저 URL은 Ticksy에 /ticket, PG에 /payment-gateway 프록시 접두사가 붙는다. 카드 PAN·CVC·비밀번호는 PG로 직접 전송하며 Ticksy는 세션 발급과 결과 통보만 처리한다.

## 서비스와 트랜잭션

- Ticksy GatewayCardCheckoutService는 NEVER로 외부 트랜잭션을 거절한다. GatewayCardCheckoutCommands의 예매 → Payment 잠금은 HTTP 전후의 짧은 트랜잭션에서만 유지한다.
- 신규 카드 Payment는 기존 gateway_expires_at에 최초 승인 기한을 고정한다. PG prepare의 금액·구매자·기한은 저장한 최초 값으로 구성한다.
- prepare 응답의 merchant·번호·수단·은행 없음·금액·기한·PG ID를 검증한 뒤 바인딩을 커밋한다. ID 저장 전에는 브라우저에 세션을 전달하지 않는다.
- 세션 발급은 userId·paymentNo로 기존 Payment를 조회하고 연결된 PG ID로 세션만 요청한다. PG prepare를 호출하지 않으며, PG ID가 없으면 결제 준비를 먼저 완료하도록 거절한다. 준비 응답 유실은 같은 checkout 요청을 다시 보내 복구한다.
- 준비 응답 유실이나 바인딩 실패는 최초 checkout 키와 같은 조건의 수동 재요청으로 복구한다. 이 경로는 카드 승인 POST를 자동 재시도하지 않는다. 준비 결과 미확인은 503 GATEWAY_CARD_PREPARATION_UNCONFIRMED로 안내한다.
- 금융 성공과 PG 저장은 독립 커밋이다. PG 금융 상태를 Ticksy 예매 완료로 간주하지 않는다.

## 결제 세션과 인증

Ticksy의 CardCheckoutSessionController는 브라우저의 세션 발급 요청을 받는다. PG의 GatewayCardCheckoutController는 가맹점의 세션 발급 요청과 브라우저의 승인·조회를 처리한다.

PG 세션 서명 키 APP_PG_CHECKOUT_SIGNING_KEY는 사용자 JWT·가맹점 credentialKey·서비스 토큰과 분리한다. 최소 32바이트 이상의 무작위 값으로 외부 설정에서 주입한다. 로컬은 git 제외된 payment-gateway-service/pg-checkout.properties에 생성한 키를 두고 optional config import로 읽는다. 배포 환경은 해당 환경변수를 설정해야 한다. 원문은 소스·DDL·로그에 넣지 않는다.

세션은 issuer=payment-gateway, audience=card-checkout, tokenType=pg-card-session, merchantId, pgPaymentId, paymentNo, buyerReference, paymentMethod=CREDIT_CARD, methodRevision, scope, 발급·만료 시각에 서명한다. 서명 키와 세션 자체는 서로 다른 값이다.

- 발급은 기존 가맹점 인증 필터가 Merchant·credentialKey를 검증하는 서버 전용 API다.
- 브라우저 승인·조회는 사용자 JWT가 필요하며 별도로 세션 서명·용도·구매자·거래·수단·revision을 DB 원거래와 대조한다. 일반 요청의 merchantId로 인증하지 않는다.
- 승인 세션은 최대 5분이며 원래 승인 기한을 넘기지 않는다. 로컬 결제가 취소·완료됐거나 PG 승인 기한이 지났으면 조회 전용 세션을 발급한다.
- 조회 전용 세션은 승인 기한 이후에도 최대 5분 사용하고 재발급할 수 있다. 금융 결과 확인만 하며 새 승인에는 사용할 수 없다.
- 브라우저는 세션을 컴포넌트 메모리에만 보관한다. sessionStorage에는 기존 결제 식별자와 처리 중 표시만 남긴다. 새로고침·세션 만료 시 Ticksy에서 소유 거래의 세션을 다시 받는다.
- 서명 키 교체로 기존 세션이 무효화돼도 같은 PG 거래로 새 세션을 발급한다. 키의 단계적 교체/다중 키 운영 기능은 이번 범위에 포함하지 않는다.

## 응답 유실과 완료 판정

1. 승인 응답 유실: 브라우저는 같은 PG ID를 GET으로 조회한다. PG 승인 POST를 자동 반복하지 않는다.
2. 카드사 성공 후 PG 이력·Outbox 저장 실패: GET이 카드사의 원승인을 조회하고 금융 식별 해시·원거래·금액·카드사·승인 시각을 대조해 PG 상태·이력·Outbox를 함께 복구한다. PAN·CVC·비밀번호를 복원하거나 재전송하지 않는다.
3. 금융 원승인도 없으면 404다. 승인 세션이 유효하면 같은 PG ID로 카드 입력을 다시 받고, 조회 전용 세션이면 기한 만료를 안내한다.
4. PG PAID라도 통보 PENDING/SENDING이면 브라우저는 대기한다. DELIVERED이고 PAID인 경우에만 완료한다. CANCELLED/EXPIRED/FAILED/환불 상태는 완료로 표시하지 않는다.
5. Ticksy는 통보의 pgPaymentId가 저장한 Payment.pgPaymentId와 일치해야 반영한다. 중복 통보는 같은 원승인 번호로 재응답하며 다른 거래·금액·승인은 거절한다.
6. Ticksy가 취소·만료 등으로 확정 거절하면 기존 PG 통보 처리의 원승인 보상을 실행한다. 통신 오류는 확정 거절로 취급하지 않는다.

은행/PG/가맹점 Outbox 전달은 기존 재시도를 유지한다. 금융 성공 후 PG 확정 전 프로세스가 중단되고 브라우저도 더 이상 조회하지 않으면 조회 복구나 관리자 확인이 필요하다. 카드 인증값을 저장하는 자동 재승인 스케줄러는 추가하지 않는다.

## 호환성·스키마·남은 범위

- 신규 checkout에서 카드 준비 기한을 저장한 결제만 새 PG 준비 흐름을 사용한다. 기존 CARD의 gateway_expires_at·pg_payment_id가 NULL이면 기존 사용자 JWT 승인·조회 경로를 유지한다. 기존 데이터를 임의로 신규 금융 거래에 연결하지 않는다.
- 신규 PG 거래는 브라우저의 새 /card/checkout 경로를 사용한다. 기존 /card/gateway 경로의 가맹점 서버 승인·조회 API도 유지한다.
- 신규 PG 카드를 기존 카드 승인 validate/settle 경로로 처리하지 않도록 차단한다. 프런트·Ticksy·PG를 함께 반영해야 한다.
- 신규 PG 카드의 카드 → 무통장 전환은 카드사의 승인 차단 확정과 기존 승인 복구를 거친다. 같은 PG ID를 유지하고 수단 버전을 올려 이전 세션을 무효화한다. [전환·경합·응답 유실 계약](payment-gateway-card-method-change.md)을 따른다. PG 미연결 기존 카드의 기존 전환 경로는 유지한다.
- 신규 PG 카드 환불·철회는 공통 신청 API로 연결했다. [카드 환불 계약](payment-gateway-card-refund.md)을 따른다. 기존 카드·구계좌 환불과 새 PG 가상계좌 환불도 유지한다.
- 이번 작업은 기존 payments.pg_payment_id·gateway_expires_at을 사용한다. 테이블·컬럼 추가와 데이터 이전 SQL은 없으며 실제 DB 삭제·초기화는 수행하지 않는다. 세션 전용 테이블도 만들지 않는다.

## 검증

PG 통합 테스트는 브라우저 승인·중복 승인·가맹점 통보 완료·다른 사용자/가맹점/거래/revision 차단·조회 세션의 승인 차단·PG 저장 실패 후 GET 복구를 검증한다. 서명 단위 테스트는 기한·별도 키·위변조·사용자·조회 범위를 확인한다.

Ticksy 통합 테스트는 로컬 준비 커밋 → 실제 가맹점 인증 HTTP prepare → PG ID 커밋 → 세션 발급 순서, 최초 요청 재사용, 소유권·응답 조건 검증, PG ID가 다른 통보 차단, 중복 통보와 예매/티켓/Payment 완료를 검증한다.

브라우저 테스트는 카드 원문의 PG 전용 전송, 메모리 세션, 새로고침 후 세션 재발급, 자동 승인 재전송 금지, 금융 PAID와 가맹점 반영 완료의 구분을 검증한다.

2026-11-15 실행 결과:

- 공통 모듈 45개, PG 전체 362개, Ticksy 결제·checkout·환불 회귀 291개를 검증했다. 기존 통보 테스트 2개의 데이터에 PG ID 연결을 추가한 뒤 해당 테스트 클래스를 재실행했으며 최종 실패·건너뜀은 0개다.
- PostgreSQL 검증을 활성화했다. 실제 PG DB의 기존 스키마 검증은 읽기 전용으로 수행했고, Ticksy checkout·환불 동시성 및 신규 카드 checkout 테스트는 별도의 일회용 PostgreSQL 컨테이너에서 실행했다. 테스트 컨테이너는 종료 후 정리했다.
- 프런트엔드 테스트 37개, TypeScript/Vite 빌드, PG·Ticksy bootJar 빌드가 통과했다.
- 세션 조회를 PG prepare와 분리한 뒤 GatewayCardCheckoutIntegrationTest 8개를 H2에서 재실행해 통과했다. PG ID가 없는 세션 요청은 PG 준비·세션 HTTP를 모두 실행하지 않는 것도 확인했다.
- 실행 중인 전체 서비스와 실제 브라우저를 연결한 수동 결제는 이번 검증에 포함하지 않았다. HTTP 연동은 통합 테스트의 금융/가맹점 서버와 실제 Feign·RestClient를 사용해 확인했다.
