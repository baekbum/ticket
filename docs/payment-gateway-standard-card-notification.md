# PG 공통 카드 통보 계약과 URL 기반 전송

2026-11-09 가맹점별 발신 어댑터를 없애고 하나의 공통 전송 구현으로 변경했다. PG가 통보 규격을 정의하고, Ticksy 등 각 가맹점이 그 규격을 받는 수신 API를 구현한다.

같은 날 [은행 입금 통보](payment-gateway-account-deposit.md)를 확장했다. 신규 표준 계약은 PAYMENT_NOTIFICATION_V1이고 Ticksy의 공통 수신 경로는 /api/v1/payments/internal/gateway/notifications다. 기존 카드 DTO·전용 수신 API와 CARD_NOTIFICATION_V1 코드도 호환한다. 아래 카드 도입 당시 검증/등록 기록과 현재 공통 수신 API의 범위를 구분한다.

## 역할과 호출

~~~text
PG Outbox Dispatcher
 → 등록된 가맹점의 규격 코드·대상 키 확인
 → MerchantNotificationTargetResolver에서 전체 URL·인증값 조회
 → MerchantCardNotificationAdapter.send(url, token, commonEvent)
     └─ StandardCardNotificationAdapter 하나로 모든 가맹점 전송
 → 가맹점 수신 Controller/Service
 → 공통 업무 수락/확정 거절 응답
~~~

PG의 MerchantCardNotificationAdapter는 내부 발신 인터페이스다. 가맹점은 이 Java 인터페이스를 구현하지 않고 공통 HTTP/JSON 계약을 수신한다. 기존 TicksyCardNotificationAdapter와 MerchantCardNotificationRouter는 제거했다. 대상 주소 선택은 등록 설정 조회이며, 가맹점별 클래스 선택이 아니다.

## URL 인자와 가맹점 설정

~~~java
MerchantNotificationTarget target = targets.resolve(snapshot.merchantId(), snapshot.notificationContract(), snapshot.targetKey());
boolean accepted = sender.send(target.url(), target.serviceToken(), snapshot.toNotification());
~~~

send의 URL은 내부 인자다. 일반 결제 요청의 notificationUrl이나 사용자 헤더를 URL 선택에 사용하지 않는다. Merchant.notificationTargetKey와 Outbox에 보존한 대상 키를 통해 merchant_notification_endpoints에서 가맹점 ID와 대상 키를 함께 조회해 전체 API URL을 가져온다.

가맹점별 URL과 토큰은 YAML에 등록하지 않는다. 새 테이블의 구조는 다음과 같다.

| 컬럼 | 역할 |
| --- | --- |
| endpoint_id | 내부 PK |
| merchant_id | merchants FK, 설정 소유 가맹점 |
| target_key | Merchant·Outbox가 보존하는 대상 키 |
| notification_url | 경로를 포함한 전체 수신 API URL |
| service_token_ciphertext | AES-256-GCM으로 암호화한 발신 인증값 |
| active | 통보 실행 허용 여부 |
| created_at / updated_at | 등록·변경 시각 |

UNIQUE(merchant_id, target_key)로 같은 키를 가맹점별로 사용할 수 있다. 조회도 두 값을 함께 사용한다. 등록·변경은 내부 MerchantNotificationEndpointService.register/update/changeActive로 처리하며 일반 결제 요청에는 노출하지 않는다. 관리 화면/API는 이번 범위에 추가하지 않았다.

가맹점 추가에는 DB 등록만 필요하며 배포·재시작·가맹점별 클래스 추가가 필요 없다. 가맹점이 PG에 보내는 요청의 인증은 별개이며 [DB 가맹점 인증](payment-gateway-merchant-authentication.md)의 Merchant·credentialKey 헤더를 사용한다.

가맹점마다 호스트와 경로가 달라도 같은 전송기를 사용한다. 전송기는 인자 URL 뒤에 Ticksy 경로를 덧붙이지 않는다. HTTP/HTTPS의 절대 URL만 허용하고 사용자 정보·query·fragment가 포함된 주소는 거절하며 리다이렉트를 따라가지 않는다. 복호화한 인증값은 HTTP 헤더에만 사용하고 payload·로그·toString에 노출하지 않는다. DB에는 암호문만 저장한다.

Merchant.notificationContract와 Outbox의 notificationContract는 가맹점명이 아니라 통보 DTO 필드 구성·타입·의미와 응답 처리 규칙을 묶은 계약 버전 CARD_NOTIFICATION_V1을 의미한다. 향후 V2가 다른 필드/응답을 사용하면 구분할 수 있지만 상수만으로 DTO나 클래스를 자동 선택하지 않는다. 현재는 V1만 검사하고 CardNotificationSnapshot.toNotification()이 고정 DTO를 생성한다.

자바 필드·getter·중간 DTO의 이름은 notificationContract로 통일하고 DB 컬럼은 notification_contract로 매핑한다. merchants와 gateway_card_notification_outbox의 기존 컬럼은 데이터와 제약 조건을 보존하는 컬럼 이름 변경으로 반영했다. 별도 마이그레이션 파일은 추가하지 않았다. targetKey는 merchant_id와 함께 DB 통보 대상의 참조다. Outbox는 같은 가맹점·키·계약·이벤트를 재사용하며, URL/토큰을 갱신하면 이후 재시도부터 새 설정을 사용한다. 대상 키를 다른 가맹점에 재할당하지 않는다.

## 공통 요청

DTO는 common 모듈의 dev.bum.common.service.paymentgateway.notification.CardPaymentNotification이다.

| 필드 | 의미 |
| --- | --- |
| version | 1 |
| eventId | {pgPaymentId}:APPROVE:INITIAL, 재전달에도 고정 |
| merchantId | 등록 가맹점 코드 |
| pgPaymentId | PG 원거래 |
| merchantPaymentNo | 가맹점 원결제번호 |
| buyerReference | 가맹점이 확인한 사용자 |
| paymentMethod | CREDIT_CARD |
| status | 승인 발생 당시 PAID |
| amount | 원승인 금액 |
| transactionId | 카드사 원승인 번호 |
| cardCompany | 카드사 |
| maskedCardNumber | 마스킹 카드번호 |
| approvedAt | 최초 금융 승인 시각 |

X-Service-Token은 대상 가맹점의 등록 인증값이며 X-PG-Event-Id와 X-PG-Payment-Id도 같은 이벤트/거래로 전달한다. PAN 원문·CVC·카드 비밀번호는 포함하지 않는다.

## 공통 응답과 업무 의미

응답 DTO는 CardPaymentNotificationResponse다. version, eventId, merchantId, pgPaymentId, merchantPaymentNo, transactionId, amount를 그대로 돌려주며 outcome은 다음 중 하나다.

- ACCEPTED: 가맹점이 결제 반영을 완료했거나 같은 원승인의 이미 완료된 결과를 확인했다.
- REJECTED: 가맹점이 취소·만료 등으로 결제를 받을 수 없음을 확정했다.

HTTP 2xx만으로 Outbox를 완료하지 않는다. 동일 이벤트/거래/가맹점/금액인지 검증한 공통 응답이 필요하다. 빈 응답·잘못된 필드·알 수 없는 결과·통신 오류는 통보 실패로 재시도하며 PG 금융 PAID를 실패로 바꾸지 않는다. 확정 REJECTED에 대해서만 기존 카드 원승인 보상 후 PG 취소와 Outbox 완료를 확정한다.

이 계약은 단순 수신 ACK가 아니라 업무 반영 결과를 응답하는 카드 통보 계약이다. 수신부는 업무 상태와 응답 결과를 원자적으로 확정하고, 같은 eventId/transactionId의 재전달을 멱등 처리해야 한다.

## Ticksy 수신부

Ticksy에 다음 수신 경로를 추가했다.

~~~http
POST /api/v1/payments/internal/gateway/card/notifications
X-Service-Token: {Ticksy에 등록된 PG 서비스 인증값}
~~~

GatewayCardNotificationController → GatewayCardNotificationService가 공통 요청을 검증하고 기존 PaymentService.settleCardFromGateway를 호출한다. 사용자 소유권·금액·카드 원승인 번호 검증, 결제·예매·티켓·좌석 반영, 취소/만료와 기존 멱등 처리를 재사용한다. Ticksy 내부 PaymentResponse와 COMPLETED/REJECTED 결과 해석은 Ticksy 수신 서비스 안에 남으며 PG 전송기가 이를 알 필요가 없다.

app.payment-gateway.merchant-id는 PG가 Ticksy에 발급한 코드다. 기본 개발 설정은 1이며 APP_PG_MERCHANT_ID로 지정한다. 수신 인증 후에도 본문의 merchantId가 이 설정과 다르면 결제 조회 전에 403으로 거절한다. 기존 내부 결제 보안 체인을 사용하므로 사용자 JWT만으로 수신 API를 호출할 수 없다.

Ticksy는 기존 transactionId 기반 결제 멱등 처리를 재사용하므로 이 변경에 별도 Inbox나 DB 컬럼을 추가하지 않는다. PG ID는 공통 요청/응답 및 이벤트 식별에 사용하며 Ticksy Payment에 PG ID를 저장하는 checkout 호출 전환은 별도 단계다.

## 적용과 호환성

- 기존 사용자 JWT 카드 API, Ticksy checkout, 기존 /internal/card/settle 엔드포인트는 유지한다.
- 신규 카드 Outbox 경로만 공통 DTO/응답으로 바꾼다. 기존에 구현한 금융 승인·PG 확정·Outbox 임대·재시도·승인 보상의 트랜잭션 경계는 유지한다.
- MerchantNotificationProperties와 가맹점별 YAML targets 목록을 제거하고 merchant_notification_endpoints를 사용한다. 값은 기본 서버 주소가 아닌 전체 수신 API URL이다.
- 기존 TICKSY 계약 코드와 구 payload를 기대하는 수신부는 공통 계약으로 함께 전환해야 한다. 수신부 배포 → URL/등록 코드 변경 → 새 PG 전송기 배포 순서를 맞춘다. 미전달 이벤트가 있다면 실행 중인 임대와 기존 수신 결과를 확인하고 동일 이벤트/원승인을 유지한 채 전환해야 한다.
- 로컬 DB에는 새 통보 대상 테이블만 추가하고 기존 Ticksy URL·서비스 토큰을 암호화해 등록했다. 기존 금융 테이블·데이터는 삭제하거나 초기화하지 않는다.
- initData.sql은 새 가맹점 등록에 공통 규격 코드를 사용한다. 환경별 통보 대상은 내부 등록 서비스로 별도 등록하며 평문 토큰이나 고정 암호문을 SQL에 넣지 않는다.

## 검증

PG 통합 테스트는 서로 다른 URL/경로의 두 가맹점에 구현체 하나로 같은 DTO를 보내는지, URL을 덧붙이지 않는지, 요청 본문이 등록 URL을 바꾸지 못하는지, 리다이렉트·다른 이벤트/가맹점/승인/금액 응답을 완료로 처리하지 않는지 확인한다.

Ticksy 수신 테스트는 내부 인증·다른 가맹점/잘못된 규격 차단·공통 응답·재전달을 확인한다. 실제 H2 결제 모델에서도 결제/예매/티켓/좌석 반영과 중복 이력 방지, 취소 거래의 늦은 통보 거절을 검증한다.

2026-11-09 공통 통보 규격 도입 당시 회귀 테스트 330개가 실패·오류·건너뜀 없이 통과했다. 이후 DB 통보 대상 전환의 검증 결과는 아래에 별도로 기록한다.

| 범위 | 클래스 | 테스트 |
| --- | --- | --- |
| PG 서비스/보안/설정/게이트웨이 JPA | 25 | 223 |
| Ticksy 결제 서비스/모델/기존·신규 수신/checkout/Feign | 18 | 107 |
| 합계 | 43 | 330 |

PG 범위에는 신규 공통 통보 카드 통합 28개, 등록 URL 해석 검사 3개, 실제 PostgreSQL validate/롤백 15개가 포함된다. Ticksy 범위에는 표준 수신 Controller 검사 8개와 실제 H2 결제 모델의 표준 카드 완료/재전달·취소 거절 검사 2개가 포함된다.

~~~powershell
.\gradlew.bat -g D:\.gradle :payment-gateway-service:test --tests '*.service.*' --tests '*.security.*' --tests '*.config.*' --tests '*.jpa.gateway.*' :ticket-service:test --tests '*.service.payment.*' --tests '*.jpa.payment.PaymentModelIntegrationTest' --tests '*.service.CheckoutPaymentServiceTest' --tests '*.config.PaymentGatewayFeignConfigTest' --tests '*.controller.PaymentControllerTest' --tests '*.controller.GatewayCardNotificationControllerTest' --offline --no-daemon --console=plain
~~~

실제 PG 검증 연결 정보는 GATEWAY_CORE_VERIFY_URL/USER/PASSWORD로 주입하고 기존 임시 PostgreSQL 테스트 포트 변수는 설정하지 않았다. 나머지 영속성 검사는 격리 H2를 사용했다. 실제 DB는 public 테이블 56개·가맹점 1건을 유지하며 PG 거래/이력·카드 승인/통보 Outbox·은행 계좌는 검증 후 모두 0건이다.

생성기가 추가한 Querydsl 소스의 끝 빈 줄만 커밋된 코드와 동일함을 확인하고 정리했다. 기존 Router/가맹점별 발신 구현체의 컴파일 출력이 남지 않았고 git diff --check도 통과했다.

## DB 통보 대상 운영

전용 환경 변수 APP_MERCHANT_NOTIFICATION_ENCRYPTION_KEY를 전역 암호화 비밀값으로 주입한다. 가맹점별 토큰은 AES-256-GCM 암호문으로만 저장하고 nonce·인증 태그를 함께 보존한다. 같은 키를 모든 PG 인스턴스에 지속적으로 제공해야 한다. 로컬 호환 설정에서 전용 키가 없으면 기존 app.ticket.service-token을 재사용한다. 운영에서는 별도 키를 사용하며, 기존 암호문이 있다면 키 변경 전에 이전 키로 복호화하고 새 키로 다시 암호화해야 한다. 내부 서비스 토큰을 암호화 키로 재사용 중인 환경에서도 토큰 회전 전에 이 절차가 필요하다.

등록 예시는 MerchantNotificationEndpointService.register(merchantId, targetKey, fullUrl, outboundServiceToken)이다. URL/키 길이와 형식을 검증하고 토큰을 암호화한 뒤 한 로컬 트랜잭션에서 저장한다. 일반 결제 요청은 이 메서드를 호출하지 못한다. 인증값의 발급·관리 화면과 KMS 기반 키 회전 자동화는 별도 작업이다.

Resolver는 짧은 read-only 트랜잭션에서 소유 가맹점·활성 상태·URL·암호문을 확인해 전송용 값을 반환한다. 해당 트랜잭션이 끝난 후 외부 HTTP를 수행하므로 설정 행이나 금융 행의 락을 통보 중 유지하지 않는다. DB 조회·복호화·대상 비활성화 실패는 기존 Outbox의 재시도로 남고 PAID를 금융 실패로 바꾸지 않는다.

## DB 전환 검증 (2026-11-09)

PG 서비스·보안·설정·실제 PostgreSQL 모델 검증 26개 클래스, 233개 테스트가 실패·오류·건너뜀 없이 통과했다. 실제 PostgreSQL 17개, 카드 HTTP 통합 31개, 대상 Resolver 5개, 토큰 암호화 3개를 포함한다.

같은 가맹점의 중복 키는 거절하고 다른 가맹점의 같은 키는 분리한다. 다른 가맹점 대상 조회·변경, 요청 URL 덮어쓰기, 비활성 대상 실행을 차단한다. DB URL 변경의 즉시 반영, 비활성화/재활성화 후 같은 이벤트 재시도, 암호문 영속화·변조/잘못된 키 거절과 기존 결제/환불/Outbox 회귀를 검증했다.

실제 local-postgres/ticket DB는 public 테이블 57개이며 Ticksy 1개 통보 대상을 기존 설정에서 이전했다. 암호화·복호화 일치를 검증했으며 원문/암호화 키는 소스·SQL·로그에 기록하지 않았다. 기존 테이블 삭제·초기화는 수행하지 않았고 별도 마이그레이션 SQL 파일도 만들지 않았다. 실제 DB 검증 테스트의 데이터 변경은 롤백했다.
