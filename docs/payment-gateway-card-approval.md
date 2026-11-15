# PG 거래 기반 카드 승인

2026-11-15 Ticksy 신규 카드 checkout의 PG prepare·ID 저장과 브라우저의 거래 전용 세션 승인·조회, 공통 결과 통보를 연결했다. 실제 호출·인증·응답 유실 복구·기존 카드 호환 및 현재 수단 전환/환불 제한은 [카드 checkout 연동](ticksy-gateway-card-checkout.md)을 우선한다.

2026-11-09 가맹점 통보를 [공통 계약과 URL 인자 기반 전송](payment-gateway-standard-card-notification.md)으로 변경했다. PG 전송 구현체는 하나이며 Ticksy 등 가맹점 수신부가 공통 규격을 구현한다. 금융 승인/조회·Outbox 재시도·승인 보상 흐름은 유지한다.

2026-11-08 준비된 PG 거래를 카드사의 실제 원승인과 연결했다. 기존 프런트의 사용자 JWT 카드 API와 Ticksy 호출은 유지하며 신규 서버 API를 별도로 제공한다.

## 분석과 적용 범위

기존 프런트는 사용자 JWT로 POST /api/v1/payments/card/approve를 호출한다. PG의 HTTP 중계를 거친 기존 카드 승인 서비스가 Ticksy 검증 API를 호출하고, 승인 커밋 뒤 Ticksy settle 결과를 반영한다. 기존 원승인은 paymentNo 전역 유니크이며 금융/가맹점 통보 상태가 카드 거래 행에 섞여 있다.

새 경로는 준비된 gateway_payments의 가맹점·예매자·금액·저장된 결제수단을 PG가 확인한다. 카드사는 Ticksy를 호출하지 않고 카드 인증·한도·원승인만 처리한다. PG가 PAID와 APPROVE 이력, 가맹점 통보 Outbox를 함께 저장한다. 기존 사용자 JWT API는 미연결 카드의 호환 경로다. 신규 카드 checkout은 거래 전용 세션과 별도 브라우저 승인 API를 사용한다.

| 책임 | 구현 |
| --- | --- |
| PG 승인·조회 진입점 | controller.gateway.payment.GatewayCardPaymentController |
| PG 승인 조정/확정 | service.gateway.card.GatewayCardApprovalService, GatewayCardApprovalCommandService |
| 금융 HTTP 중계 | service.gateway.client.FinancialSimulationGatewayService |
| 카드사 진입점 | controller.financial.CardController |
| Ticksy 의존성 없는 금융 승인 | service.financial.card.CardApprovalService, CardApprovalCommandService |
| 기존/신규 공통 카드 인증 | support.CardCredentials와 기존 카드 원장의 한도 메서드 |
| 가맹점 결과 통보·재시도 | service.gateway.notification |

기존 금융 서비스와 이름이 같은 새 PG 승인 서비스는 명시적인 빈 이름 pgCardApprovalService를 사용해 전체 컴포넌트 스캔에서도 충돌하지 않는다.

## API 계약과 인증

1. 가맹점 서버가 기존 prepare에 CREDIT_CARD, 최종 금액, 예매자, 승인 기한을 보내 PG 거래를 준비한다.
2. 반환된 pgPaymentId로 새 승인 API를 호출한다.

~~~http
POST /api/v1/payments/card/gateway/{pgPaymentId}/approve
Merchant: {숫자 가맹점 코드}
credentialKey: {PG가 발급한 원문 키}
Content-Type: application/json
~~~

~~~json
{
  "buyerReference": "buyer-1",
  "cardCompany": "SHINHAN",
  "cardNumber": "4111111111111111",
  "cvc": "123",
  "cardPassword": "12",
  "amount": 10000
}
~~~

카드 값은 모의 카드 설명용 예시다. 선택적인 merchantId는 principal과의 일치 검사에만 사용한다. 본문이나 임의 사용자 헤더로 가맹점을 선택하지 않는다. 새 API는 가맹점 서버 인증용이며 브라우저에 서비스 토큰을 전달하지 않는다. buyerReference는 가맹점이 확인한 원거래 예매자와 대조한다. 기존처럼 카드 소유자와 예매자가 다른 결제도 카드 인증값이 유효하면 허용하며 원장 한도/사용액은 외부 응답에 노출하지 않는다.

~~~json
{
  "pgPaymentId": "20261108550e8400-e29b-41d4-a716-446655440000",
  "merchantId": 1,
  "paymentNo": "PAY-1",
  "buyerReference": "buyer-1",
  "cardCompany": "SHINHAN",
  "transactionId": "CARD-20261108550e8400-e29b-41d4-a716-446655440001",
  "maskedCardNumber": "4111-****-****-1111",
  "amount": 10000.00,
  "approvedAt": "2026-11-08T12:00:00",
  "pgStatus": "PAID",
  "notificationStatus": "PENDING"
}
~~~

GET /api/v1/payments/card/gateway/{pgPaymentId}로 같은 가맹점의 원승인 스냅샷과 현재 PG/통보 상태를 조회한다. 다른 가맹점/없는 거래/미승인 결과는 404다. 금융 상태 PAID는 Ticksy 통보 완료를 뜻하지 않는다. 초기 응답은 금융 승인 성공을 200으로 반환하고 가맹점 결과는 비동기 통보한다.

| 상황 | 처리 |
| --- | --- |
| 잘못된 가맹점 인증 | 401 |
| 본문 가맹점 불일치, 신규 실행 시 정지 가맹점 | 403 |
| 다른 가맹점의 PG 거래 | 404 |
| 같은 거래의 다른 금액·예매자·카드, 저장된 BANK_TRANSFER 거래에 카드 요청 | 409 |
| 카드 인증/한도/유효기간 거절 | 400 CARD_APPROVAL_REJECTED |
| 승인 기한 만료, 통보 계약/대상 미등록 | 신규 실행 거절 |
| 금융 통신/응답 확인 불가 | 503 FINANCIAL_PROVIDER_UNAVAILABLE, PG 금융 실패로 변경하지 않음 |
| PG 이력/Outbox 저장 실패 | 503 GATEWAY_STORAGE_UNAVAILABLE, 재요청으로 금융 원승인 조회 후 PG만 확정 |

이미 커밋된 금융 승인의 복구와 저장된 승인 재응답은 가맹점 정지/기한 경과 후에도 허용한다. 통보 설정이 일시적으로 없어져도 기존 금융 사실의 PG 반영은 허용하며 통보만 재시도한다.

## 카드사 계약과 멱등성

내부 금융 토큰으로만 아래 URL을 호출한다.

| 경로 | 역할 |
| --- | --- |
| POST /internal/financial-simulations/cards/gateway/approve | pgPaymentId, buyerReference, cardCompany, cardNumber, cvc, cardPassword, amount, expiresAt로 승인 |
| GET /internal/financial-simulations/cards/gateway/{pgPaymentId} | 금융 원승인 조회, 미승인 404 |
| POST /internal/financial-simulations/cards/gateway/{pgPaymentId}/cancel | 확정적인 가맹점 거절에 대한 원승인 전체 보상 |

신규 원승인의 pg_payment_id는 PG FK/UNIQUE다. 기존 NOT NULL/UNIQUE payment_no에는 내부 pgPaymentId를 저장한다. 가맹점 원번호는 PG 거래에 유지하므로 서로 다른 가맹점의 동일 결제번호가 충돌하지 않는다. 카드 원장에는 PG ID를 저장하지 않으며 하나의 카드로 여러 PG 원승인을 처리한다.

원장 행을 잠가 서로 다른 PG 거래의 한도 경합과 누적액 유실을 막는다. 같은 PG ID의 최초 동시 승인에서 다른 카드가 들어와도 유니크 충돌 트랜잭션을 롤백한 후 새 트랜잭션으로 승자를 확인한다. 다른 카드는 409로 거절하며 패자의 카드 사용액은 롤백된다.

PG 승인 작업은 APPROVE / INITIAL로 한 번 기록한다. 작업 해시는 준비 내용과 카드 식별 해시를 포함하며 같은 키의 다른 카드/금액을 거절한다. PAN 원문·CVC·비밀번호는 PG 이력과 Outbox에 저장하지 않고 record의 toString도 비밀값을 가린다. 성공한 재요청은 기존 승인번호/마스킹 정보만 반환한다. 재응답 시 인증 비밀값을 다시 금융 실행하거나 저장하지 않는 정책은 기존 승인 재응답과 같다.

## 트랜잭션과 복구

~~~text
PG 원거래 검증/잠금 → 커밋·잠금 해제
 → 카드사 HTTP GET 기존 승인 확인
 → 필요할 때만 HTTP POST 승인
     [금융 TX: 카드 사용액 + 원승인 저장]
 → PG 확정
     [PG TX: PAID + APPROVE 이력 + 가맹점 통보 Outbox]
 → 200 승인 응답
~~~

같은 애플리케이션과 DB라도 HTTP 요청 사이의 원자적 커밋은 없다. 금융 커밋 뒤 응답 유실/PG 저장 실패 시 원승인은 남고 PG는 READY일 수 있다. 같은 승인 요청의 재시도에서 카드사 GET으로 원승인을 찾아 PG만 확정한다. 이 복구는 호출자의 재요청을 전제로 하며 카드 비밀정보를 저장하는 자동 재승인 스케줄러는 추가하지 않았다.

가맹점 통보는 30초 주기 스케줄러가 수행한다. 짧은 트랜잭션에서 60초 임대를 커밋한 뒤 HTTP를 수행하고 별도 트랜잭션에서 결과를 반영한다. 프로세스가 중단되면 임대 만료 후 재전달하며 attempt 세대로 늦은 이전 워커 응답이 새 결과를 덮어쓰지 못하게 한다. 통보 중 PG 행/Outbox 행을 잠근 DB 트랜잭션을 유지하지 않는다.

Ticksy의 확정 REJECTED 응답이면 카드사에 원승인 보상을 HTTP로 요청한다. 카드사 한도 복원+승인 CANCELLED를 먼저 커밋하고, PG의 CANCELLED+CANCEL_APPROVAL 이력+Outbox REJECTED/DELIVERED를 함께 확정한다. 응답 유실/PG 저장 실패는 같은 거래·같은 이벤트의 멱등 보상으로 복구한다. 통신 실패, 잘못된 응답, 결과 확인 불가는 승인 보상 사유로 취급하지 않는다.

## 가맹점 통보 대상과 계약

MerchantNotificationTargetResolver가 Outbox의 공통 규격 코드와 등록 대상 키에서 전체 수신 URL과 인증값을 해석한다. StandardCardNotificationAdapter.send(url, token, event)는 모든 가맹점에 같은 공통 DTO를 전송한다. 가맹점별 Router/발신 구현체는 없다.

merchant_notification_endpoints에서 merchant_id + target_key로 전체 API URL과 암호화한 발신 토큰을 조회한다. 가맹점별 YAML targets 목록은 제거했다. 등록·변경은 MerchantNotificationEndpointService로 수행하고 암호화 키만 환경 설정에 둔다. URL/인증값 변경은 재시작 없이 다음 통보부터 반영된다. 결제 요청에서 통보 주소를 받지 않으며 HTTP 리다이렉트를 따라가지 않는다.

매번 같은 가맹점 원번호·원승인 번호와 X-PG-Payment-Id, X-PG-Event-Id를 전달한다. 이벤트 ID는 {pgPaymentId}:APPROVE:INITIAL로 고정한다. 모든 가맹점이 공통 요청/응답 계약을 구현하며 PG에 별도 가맹점 클래스를 추가하지 않는다. Ticksy 수신부는 기존 transactionId 기반 멱등 결제 처리를 재사용한다.

## 스키마·기존 데이터·호환성

- gateway_card_notification_outbox의 최종 정의는 schema.sql에서 관리한다. 실제 DB에 적용한 개별 마이그레이션 SQL은 사용자 요청으로 삭제했다. PG 거래/가맹점/원승인/통보 내용, 안정적인 eventId, 임대/재시도/완료 상태를 저장한다.
- (pg_payment_id, merchant_id) 복합 FK로 통보 소유 가맹점이 PG 원거래와 일치하는지 DB에서도 검증한다. 같은 PG 승인 이벤트는 한 번만 저장한다.
- 실제 local-postgres/ticket DB에는 새 테이블과 인덱스/제약만 추가했다. 기존 테이블 삭제·초기화와 과거 결제의 일괄 연결은 하지 않았다.
- 기존 카드 행은 pg_payment_id NULL로 유지한다. 검증된 merchant+원번호로 PG 거래를 준비하고 금융 사실을 대사한 뒤 별도의 승인된 이관 작업으로만 연결한다. 가맹점 식별 불가능한 행은 NULL로 남긴다.
- 기존 paymentNo 조회·환불·복구 스케줄러는 pg_payment_id IS NULL인 카드 거래만 처리한다. 신규 연결 거래가 Ticksy 고정 경로에 섞이거나 다른 가맹점에 노출되지 않는다.
- 신규 PG 카드 거래의 사용자 요청 취소·부분 환불 API는 다음 단계다. 이번 내부 cancel은 가맹점 확정 거절의 전체 원승인 보상 전용이다. 기존 카드 환불 API는 기존 NULL 연결 거래에 유지된다.
- Ticksy 신규 checkout은 PG prepare·pgPaymentId 저장·거래 전용 세션/JWT 승인으로 전환했다. 기존 미연결 카드만 기존 API를 사용한다.

## 검증

GatewayCardApprovalIntegrationTest는 외부 MockMvc → 실제 RestClient/내부 금융 인증 → H2 금융 트랜잭션과 실제 HTTP 가맹점 테스트 서버를 사용한다. 승인·한도·가맹점 범위·동시 승인·요청 불일치·중복·응답 유실·PG 이력/Outbox 롤백·통보 재시도/응답 유실·승인 보상·임대 복구를 검증한다.

GatewayCorePostgresTest는 실제 PostgreSQL의 스키마 validate와 롤백만 사용해 PAID/승인 JSON/통보 저장, 임대·완료 상태, 다른 가맹점 통보의 복합 FK 거절을 검증한다.

2026-11-08 전체 PG 선택 범위와 추가 빈 등록 검사를 합쳐 24개 클래스, 218개 테스트가 실패·오류·건너뜀 없이 통과했다.

| 실행 | 테스트 | 실패/오류/건너뜀 |
| --- | --- | --- |
| 전체 PG 선택 범위 23개 클래스 | 217 | 각 0 |
| 위 범위 중 신규 카드 HTTP/가맹점 통합 | 26 | 각 0 |
| 위 범위 중 실제 PostgreSQL validate/롤백 | 15 | 각 0 |
| 추가 신구 서비스 전체 컴포넌트 등록 검사 | 1 | 각 0 |

~~~powershell
.\gradlew.bat -g D:\.gradle :payment-gateway-service:test --tests '*.service.*' --tests '*.security.*' --tests '*.config.*' --tests '*.jpa.gateway.*' --offline --no-daemon --console=plain

.\gradlew.bat -g D:\.gradle :payment-gateway-service:test --tests '*.CardServiceRegistrationTest' --offline --no-daemon --console=plain
~~~

실제 DB 검증에는 GATEWAY_CORE_VERIFY_URL/USER/PASSWORD를 환경으로 전달하고 기존 임시 DB 전용 포트 변수는 설정하지 않았다. schema.sql 전체도 별도 검증 스키마에서 실행해 PG 테이블 11개를 생성하고 신규 Outbox의 컬럼·순서·타입/정밀도·NULL·기본값·제약 이름/정의·인덱스 이름/정의를 실제 테이블과 비교한 후 롤백했다.

최종 실제 public 테이블은 55개에서 56개로 증가했고 기존 가맹점 1건은 유지됐다. PG 거래/이력, 카드 승인, 신규 카드 통보 Outbox, 은행 계좌는 0건이며 검증 스키마도 남지 않았다. git diff --check도 통과했다.
