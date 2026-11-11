# 금융 응답과 PG 응답 DTO 분리

2026-11-11 최신 구조: 입금은 BankController의 은행 업무 API다. POST /api/v1/banks/virtual-accounts/deposit에서 은행 상태·입금 이력·은행→PG Outbox를 함께 저장하고, PG는 수신 후 Ticksy에 별도 Outbox로 통보한다. Postman·Bruno도 같은 은행 API를 호출한다. 별도 테스트 컨트롤러와 support 입금 URL은 제거했다. 기존 PG 미연결 계좌는 POST /api/v1/banks/virtual-accounts/legacy/deposit를 사용한다. 인증은 기존 X-Payment-Provider-Token을 유지하며 PG→은행의 기존 내부 업무 URL과 금융 인증도 유지한다. [은행 API 상세](payment-gateway-bank-api.md)를 참고한다.

2026-11-09: 기존 금융 HTTP 중계의 `ResponseEntity<byte[]>`와 공개 PG 컨트롤러의 `ResponseEntity<?>`를 제거했다. URL·권한·JSON 필드·200/202/오류 상태 계약은 유지하고 자바 응답 타입을 명시한다.

## 경계와 책임

~~~text
PG Controller
 → PG 서비스 (GatewayCardApiService / GatewayVirtualAccountApiService / 입금 조율 서비스)
 → FinancialSimulationGatewayService
 → RestClient
 → CardController / BankController
 → 금융 도메인

금융 도메인 결과
 → 금융 컨트롤러가 Financial...Response로 복사
 → RestClient가 지정된 금융 DTO로 역직렬화
 → PG 서비스가 원거래를 대조하고 PG DTO로 변환
 → PG Controller가 구체적인 응답 타입으로 반환
~~~

기존 금융 도메인의 내부 반환 클래스명 일부는 Gateway 접두사를 유지한다. HTTP 경계에는 `dto.financial`의 DTO를 사용하며, PG 컨트롤러가 금융 응답 객체를 그대로 반환하지 않는다. 기존 PG ID 기반 카드 승인·가상계좌 발급·입금의 전용 금융 DTO 및 PG 확정 트랜잭션은 유지한다.

## 기존 API의 타입 매핑

| API | 금융 HTTP 응답 | 외부 PG 응답 |
| --- | --- | --- |
| 카드 승인 | FinancialCardPaymentApproveResponse | GatewayCardPaymentApproveResponse |
| 카드 상태 | FinancialCardPaymentStatusResponse | GatewayCardPaymentStatusResponse |
| 카드 환불 | FinancialCardPaymentRefundResponse | GatewayCardPaymentRefundResponse |
| 계좌 발급 | FinancialVirtualAccountIssueResponse | GatewayVirtualAccountIssueResponse |
| 계좌 상태 | FinancialVirtualAccountStatusResponse | 공통 GatewayVirtualAccountStatusResponse |
| 계좌 입금 | FinancialVirtualAccountDepositResponse | GatewayVirtualAccountPaymentDepositResponse |
| 계좌 환불 | FinancialVirtualAccountRefundResponse | GatewayVirtualAccountRefundResponse |
| 계좌 취소 | FinancialVirtualAccountCancelResponse | 공통 GatewayVirtualAccountCancelResponse |
| 카드 승인 이력 존재 여부 | Boolean | Boolean |

승인 이력 조회의 루트 JSON `true`/`false`는 기존 Ticksy 계약이므로 객체로 감싸지 않는다. 모든 결제 금융 DTO는 FinancialPaymentResponse를 구현하며 성공 응답에 결제번호가 있어야 한다. PG 변환 서비스는 요청한 결제번호를 대조하고, 카드 승인에서는 인증된 사용자도 대조한다. 기존 계좌 입금에서는 요청 계좌와 금액을 확인한다. 다른 금융 거래의 응답을 요청한 거래의 성공으로 반환하지 않는다.

## 입금 응답 통일

`BankController`는 PG 연결 계좌 테스트에 ResponseEntity<BankVirtualAccountDepositResponse>, 기존 미연결 계좌 테스트에 ResponseEntity<FinancialVirtualAccountDepositResponse>를 반환한다. PG의 입금 우회 서비스는 제거했다. 은행 입금 수신 컨트롤러는 기존 BankDepositAcknowledgement를 유지한다.

기존 계좌에는 실제 PG 거래·통보 상태가 없으므로 `pgPaymentId`, `pgStatus`, `notificationStatus`만 null일 때 JSON에서 제외한다. 기존의 paymentNo·은행·계좌·입금자·금액·은행 상태·기한·입금 시각·message 필드는 유지한다. 새 PG 연결 계좌는 기존 세 PG 필드를 계속 포함한다. 가맹점이나 PG 거래를 새로 만들거나 과거 계좌를 일괄 연결하지 않는다.

## 성공·대기·오류 처리

- 200: 지정된 금융 성공 DTO로 읽고 PG DTO로 변환한다. JSON 본문이 비어 있거나 파싱할 수 없거나 결제번호가 없으면 금융 결과 확인 불가인 503으로 처리한다.
- 202: FinancialCardPaymentPendingResponse로 읽어 PENDING/결제번호/안내 문구를 검증한다. PG 예외 처리에서 GatewayCardPaymentPendingResponse로 변환해 기존 202와 필드를 유지한다. 정상 승인 DTO에 대기 필드를 섞지 않는다.
- 업무/저장소 오류: FinancialApiErrorResponse로 읽고 FinancialApiException에 HTTP 상태와 구조화된 오류를 보존한다. PG 예외 처리는 GatewayApiErrorResponse로 반환한다. 기존 code/message를 유지하며 원문 본문을 바이트 배열로 전달하지 않는다.
- 프레임워크 오류의 timestamp·숫자 status·error·path도 기존 필드로 보존한다. 대기의 문자열 PENDING과 오류의 숫자 HTTP 상태를 서로 다른 DTO로 구분한다.
- 본문 없는 인증 오류는 같은 HTTP 상태와 빈 본문을 유지한다. 없는 오류 필드를 JSON null로 새로 추가하지 않는다.
- 통신 단절이나 잘못된 성공 응답을 금융 실패/취소 확정으로 해석하지 않는다. 자동 승인/환불 재시도나 새 작업 ID 생성도 추가하지 않는다.

HTTP 상태와 JSON 의미는 유지하지만 직렬화된 원문 바이트 순서/공백은 계약으로 삼지 않는다. 임의의 미래 오류 필드를 그대로 전달하지 않으며, 응답 계약이 확장되면 금융/PG DTO와 매핑·계약 검증을 함께 갱신한다.

## 인증·트랜잭션·스키마

기존 사용자 JWT·Ticksy 서비스 토큰·입금 제공자 토큰과 내부 금융 토큰은 유지한다. 사용자 참조 헤더는 인증된 주체로 새로 생성한다. PG 변환 서비스와 금융 HTTP 클라이언트는 NEVER로 열린 DB 트랜잭션 안에서 HTTP가 실행되지 않게 한다.

이번 응답 타입 전환은 테이블·컬럼·실제 데이터 변경을 요구하지 않는다. 이전 [입금 연동](payment-gateway-account-deposit.md)의 Outbox와 대사 복구는 같은 거래/이벤트 식별자를 사용한다.

## 검증 범위

FinancialSimulationResponseContractTest는 선언한 DTO, 금액, 202 대기, 400/409/503 오류, 본문 없는 401, 프레임워크 403, 루트 boolean, 빈/잘못된/무관한 200 본문, 다른 거래/사용자 응답을 검증한다.

FinancialSimulationHttpIntegrationTest는 실제 금융 HTTP 경계를 통과해 카드 승인·사용자 범위·발급·조회·중복 입금·취소·환불·환불 프로세스 ID·금융 저장 롤백·연결 유실의 기존 계약을 확인한다. 승인/환불의 JSON 필드와 기존 입금 응답에서 PG 전용 필드가 제외되는 것도 검증한다.

## 검증 결과 (2026-11-09)

PG 서비스·보안·설정·실제 PostgreSQL 모델 28개 클래스 272개와 Ticksy Feign 설정·checkout 호출 2개 클래스 7개, 총 279개가 실패·오류·건너뜀 없이 통과했다. 새 금융 응답 계약 검증 15개와 기존 PG ID 기반 카드 승인/가상계좌 발급·입금, Outbox·취소/입금 경합·환불 회귀를 포함한다.

실행 범위는 :payment-gateway-service:test의 service/security/config/jpa.gateway와 :ticket-service:test의 PaymentGatewayFeignConfigTest/CheckoutPaymentServiceTest다. 실제 PostgreSQL 연결은 기존 GATEWAY_CORE_VERIFY_URL/USER/PASSWORD로 주입하고 DDL validate 및 테스트 데이터 롤백만 사용했다. 별도 스키마 변경이나 데이터 이관은 수행하지 않았다.

PG src/main/java에서 ResponseEntity<byte[]>, ResponseEntity<?> 및 readAllBytes 응답 전달이 남지 않은 것을 확인했다. 빌드가 재생성한 Querydsl 13개 파일은 원본과 끝 빈 줄 외 차이가 없음을 확인해 정리했다. 새 파일의 공백 검사와 git diff --check도 통과했다.
