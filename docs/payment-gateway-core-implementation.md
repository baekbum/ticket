# PG 가맹점·거래 기반 구현

이 문서는 [전체 목표 설계](payment-gateway-domain-design.md) 중 실제 적용한 기반 기능을 설명한다. 금융 모델의 날짜+UUID 문자열/FK 연결은 [금융 거래 연결 문서](payment-gateway-financial-links.md)에 정리했다. 기존 결제 진입점 연결과 카드 결제 세션은 후속 단계다.

2026-11-08 가맹점 인증을 거쳐 준비 거래에 계좌를 연결하는 [PG 가상계좌 발급](payment-gateway-account-issuance.md)을 추가했다. 발급은 READY를 WAITING_DEPOSIT로 변경하고 결과 이력을 저장한다. 기존 Ticksy 요청의 자동 전환이나 신규 연결 거래의 입금·취소·통보까지 포함한 변경은 아니다.

## 적용 범위

- `Merchant`, `GatewayPayment`, `GatewayPaymentHistory`와 가맹점 범위 리포지토리를 추가했다.
- `merchants.merchant_id`가 숫자 가맹점 코드이며 가맹점명은 중복을 허용한다.
- `gateway_payments`가 `(merchant_id, merchant_payment_no)`를 `yyyyMMdd + UUID` 형식 PG 식별자에 매핑한다. 식별자는 자바 String / DB VARCHAR(44)로 다루며 매핑 전용 테이블은 없다.
- 서버 토큰을 자격증명 그룹 → 등록 merchant로 연결하는 인증을 추가했다. 요청의 merchantId만으로 거래 범위를 정하지 않는다.
- 신규 준비와 조회 API를 추가했다. 준비는 금융 실행 없이 READY 거래와 REGISTER 이력만 같은 트랜잭션으로 저장한다.
- 동시 최초 삽입의 유니크 충돌은 실패 트랜잭션이 종료된 후 별도 트랜잭션에서 승자의 결과를 검증해 재응답한다.
- 가맹점·원번호가 같은 요청에서 금액·예매자·수단·기한·은행이 바뀌면 409로 거절한다. 수치가 같은 금액의 소수점 0은 정규화한다.

## 신규 API

모든 경로에는 `X-Service-Token`이 필요하다. 브라우저 프록시로 호출하면 경로 앞에 `/payment-gateway`가 붙는다. 일반 사용자 JWT나 입금 제공자 토큰으로 신규 서버 API를 호출할 수 없다.

| 메서드·경로 | 기능 |
| --- | --- |
| `POST /api/v1/payments/gateway/prepare` | 인증된 merchant의 거래 준비/동일 요청 replay |
| `GET /api/v1/payments/gateway/{pgPaymentId}` | 인증된 merchant 소유 PG 거래 조회 |
| `GET /api/v1/payments/gateway/by-payment-no/{paymentNo}` | merchant + 기존 결제번호 조회 |

준비 요청 예시:

```json
{
  "paymentNo": "PAY-EXAMPLE",
  "buyerReference": "buyer-1",
  "paymentMethod": "CREDIT_CARD",
  "amount": 4000,
  "expiresAt": "2099-01-01T23:59:59"
}
```

`merchantId`는 선택적인 일치 검증 필드다. 토큰에 연결된 merchant와 다르면 403이다. 계좌 준비에는 `paymentMethod: BANK_TRANSFER`와 `bankCompany`가 필요하다. 카드 준비에는 은행을 넣지 않는다. 기한은 마이크로초 단위로 정규화하며 신규 거래의 과거 기한은 거절한다. 동일 요청 재조회는 기한이 지났더라도 최초 내용을 바꾸지 않는다.

응답은 `pgPaymentId`, `merchantId`, `paymentNo`, `buyerReference`, 저장된 수단·금융사·금액·환불 누적액·PG 상태·기한·생성시각을 포함한다. 자격증명 키·토큰·요청 해시는 노출하지 않는다. 다른 merchant의 PG ID와 존재하지 않는 ID는 모두 404다.

`pgPaymentId`는 최초 저장 시 `@PrePersist`에서 한국 날짜(`Asia/Seoul`)의 `yyyyMMdd` 8자리 뒤에 하이픈을 포함한 UUID 36자를 바로 붙여 생성하는 44자 문자열이다. 예: `20261105550e8400-e29b-41d4-a716-446655440000`. 엔티티·DTO·컨트롤러·서비스·리포지토리의 타입은 모두 String이다. 재요청에서 발급일이나 ID를 새로 만들지 않는다. 조회는 유효한 날짜와 UUID를 검증하고 UUID 대소문자를 정규화한다. 이미 저장한 36자 UUID도 계속 조회하며 자동으로 접두사를 붙여 변경하지 않는다.

| 결과 | 상태 |
| --- | --- |
| 준비/동일 요청/본인 merchant 거래 조회 | 200 |
| 입력·수단·기한·유효 금액 오류 | 400 |
| 인증 헤더 누락·불일치·미등록 가맹점 | 401 |
| 본문 merchantId 위변조 또는 중단 merchant의 신규 준비 | 403 |
| 다른 merchant 거래 또는 없는 거래 | 404 |
| 동일 번호의 다른 준비 내용 | 409 |
| 가맹점 인증 DB를 조회할 수 없음 | 503 |

SUSPENDED merchant는 새 준비를 할 수 없고 기존 거래는 조회할 수 있다. 금융 취소·환불의 상태 정책은 해당 연결 단계에서 적용한다.

## 인증 설정과 기존 API 호환성

2026-11-13부터 가맹점 인증은 `Merchant: {숫자 코드}`, `credentialKey: {발급 원문 키}` 헤더와 merchants.credential_hash를 사용한다. PG YAML 토큰 목록은 제거했다. 원문 키는 PG 운영자가 발급하고 DB에는 SHA-256 해시만 저장한다. 키 교체 시 이전 키는 즉시 무효화된다. 발급 명령·로컬 설정·배포 환경·DB 전환은 [DB 가맹점 인증](payment-gateway-merchant-authentication.md)을 따른다.

**기존 금융 API는 아직 다중 가맹점으로 전환하지 않았다.** 기존 카드 사용자 JWT 경로와 Ticksy 내부 서비스 토큰 경로는 기존 계약을 유지한다. 다른 merchant의 새 토큰은 기존 발급·취소·환불·resolve API에 접근할 수 없다. 신규 ROLE_MERCHANT를 기존 ROLE_INTERNAL_SERVICE와 혼용하지 않는다. 기존 카드·계좌·환불 행에 merchant를 추정해 연결하지 않았다.

카드 승인 거래와 가상계좌에는 nullable `pg_payment_id`와 FK/유니크 제약을 추가했다. PG ID를 받는 새 생성 메서드와 내부 조회도 제공한다. 신규 PG 계좌 발급과 카드 승인 API는 이 연결 생성 메서드를 사용한다. 현재 기존 API가 만드는 금융 행은 호환성을 위해 PG 연결 값 NULL을 유지한다.

이번 prepare 응답에는 카드 결제 세션 토큰이 없다. 가맹점 서버용 PG 계좌 발급/카드 승인 API는 준비 거래를 사용한다. 기존 프런트엔드의 카드 세션 연계와 Ticksy 호출 전환은 후속 작업이며 기존 사용자 소유자 검증은 유지한다.

2026-11-06 기존 금융 API의 서비스 직접 호출은 [RestClient → 모의 은행·카드 컨트롤러](payment-gateway-financial-http.md)로 변경했다. 준비/조회 API와 가맹점별 실제 금융 연결의 후속 범위는 그대로이며, HTTP 호출은 DB 트랜잭션 밖에서 수행한다.

2026-11-08 [PG 카드 승인](payment-gateway-card-approval.md)을 추가했다. 신규 카드 승인 PAID·APPROVE 이력·가맹점 통보 Outbox를 함께 저장하며, 확정 거절에 대한 승인 보상도 같은 PG ID로 복구한다. 기존 JWT API는 유지한다. 검증은 전체 PG 217개와 추가 빈 등록 검사 1개, 총 218개가 통과했다.

## 스키마와 DB 적용

- 현재 테이블 정의: [schema.sql](../payment-gateway-service/src/main/resources/sql/schema.sql). 신규 DB는 이 파일과 [initData.sql](../payment-gateway-service/src/main/resources/sql/initData.sql)을 사용한다.
- 최초 핵심 테이블 추가용 일회성 SQL은 현재 DB 반영을 확인한 뒤 삭제했다. 이후 식별자 변경까지 포함한 최신 정의는 PG의 schema.sql을 따른다.
- 설계 기록: [DDL 초안](design/payment-gateway-core-schema.sql). 실행/배포 기준은 위 리소스 SQL이다.

최초 마이그레이션은 BEGIN/COMMIT과 트랜잭션 advisory lock으로 새 테이블 3개·인덱스·FK/체크/유니크 제약을 생성하고 비밀값 없는 Ticksy 등록 1건을 추가했다. 기존 금융 데이터 이전은 포함하지 않았다.

금융 연결·문자열 전환·길이 확장에 사용했던 개별 SQL 3개는 적용 완료 후 2026-11-06 사용자 요청으로 삭제했다. 현재 스키마의 기준은 schema.sql이다.

사용자 요청에 따라 2026-11-04 `local-postgres`의 `ticket` DB에 적용했다. **이 DB에서 다시 실행할 필요가 없다.** 적용 시 Ticksy의 숫자 코드는 DB 시퀀스로 발급하며 고정 번호를 코드에 넣지 않는다. 최종 데이터 이전 SQL은 원거래·환불·Outbox를 실제 연결하는 단계에서 별도로 추가한다.

최초 적용 후 실제 DB 조회 결과는 다음과 같다.

| 확인 항목 | 결과 |
| --- | --- |
| public 테이블 수 | 기존 52개 → 55개 |
| 가맹점 등록 | merchant_id=1, Ticksy, ACTIVE (현재 인증은 credential_hash로 전환) |
| PG 거래 / PG 이력 | 각 0건. PostgreSQL 검증용 데이터가 남지 않음 |
| 기존 payments·카드 원장/승인·계좌/은행 이력·환불 | 적용 전후 각 0건으로 동일 |
| 신규 테이블 PK·FK·유니크 제약 | 9개 확인. 금액·상태 등의 CHECK도 마이그레이션으로 생성 |

2026-11-06 최신 schema.sql을 격리된 비교 스키마에 생성하고 실제 DB와 비교했다. PG 테이블 10개의 컬럼명·순서·타입/길이/정밀도·NULL·기본값·제약·인덱스가 모두 일치해 삭제·재생성 대상은 0개였다. 비교용 스키마는 롤백했으며 실제 테이블과 데이터는 그대로 유지했다.

## 검증 방법

`GatewayMerchantIntegrationTest`는 격리된 H2에서 실제 Spring Security 필터·MVC·서비스·JPA를 실행한다. 중복 이름/번호, 교차 조회, 본문 위변조, 인증 종류, 토큰 회전, 가맹점 중단, 동시 요청과 금액 충돌, 등록 이력 실패의 전체 롤백을 확인한다. `MerchantAuthenticationServiceTest`와 `MerchantCredentialKeyTest`는 코드·DB 해시 검증과 무작위 키 발급·교체·폐기를 확인한다.

`GatewayCorePostgresTest`는 아래 환경 변수가 있을 때만 실제 PostgreSQL에 연결한다. `ddl-auto=validate`, `spring.sql.init.mode=never`를 사용하고 테스트마다 트랜잭션을 롤백한다. 임의 DDL 생성·삭제나 원데이터 초기화를 수행하지 않는다. 숫자 시퀀스는 롤백되어도 증가할 수 있다.

- `GATEWAY_CORE_VERIFY_URL`: 확인한 JDBC URL
- `GATEWAY_CORE_VERIFY_USER`: 접속 사용자
- `GATEWAY_CORE_VERIFY_PASSWORD`: 환경으로 주입한 비밀번호

기존 만료/입금 테스트의 선택적 PostgreSQL 변수 `GATEWAY_TEST_POSTGRES_PORT`, `GATEWAY_DEPOSIT_TEST_POSTGRES_PORT`는 별도 임시 DB 전용이다. 이번 검증에서는 설정하지 않고 해당 기존 테스트는 H2로 실행한다.

```powershell
.\gradlew.bat :payment-gateway-service:test `
  --tests '*.service.*' --tests '*.security.*' --tests '*.config.*' --tests '*.jpa.gateway.*' `
  :ticket-service:test --tests '*.service.payment.*' `
  --tests '*.jpa.payment.PaymentModelIntegrationTest' --tests '*.service.CheckoutPaymentServiceTest' `
  --tests '*.config.PaymentGatewayFeignConfigTest' --tests '*.controller.PaymentControllerTest' `
  --offline --no-daemon --console=plain
```

2026-11-04 위 범위의 실행 결과는 `BUILD SUCCESSFUL`이며 다음과 같다.

| 모듈 | 테스트 | 실패 | 건너뜀 |
| --- | --- | --- | --- |
| payment-gateway-service | 139 | 0 | 0 |
| ticket-service | 97 | 0 | 0 |
| 합계 | 236 | 0 | 0 |

PG 139개에는 실제 PostgreSQL 테스트 5개가 포함된다. 해시 CHAR(64)의 JPA 타입 매핑과 실제 스키마 일치, 이름/번호 범위, FK·중복 작업 제약을 검증했다. 나머지 PG·Ticksy 선택 테스트는 단위 테스트 또는 격리 H2/모의 외부 경계를 사용한다. 기존 카드·계좌 입금·입금 전 취소 Outbox·환불 회귀를 포함한다. 후속 금융 연결 단계의 새 시나리오까지 검증 완료한 것으로 해석하지 않는다.

2026-11-05 금융 모델의 UUID 연결을 추가한 뒤 PG 범위는 155개(실제 PostgreSQL 12개 포함)를 재실행해 모두 통과했다. 해당 단계의 변경과 검증 결과는 [금융 거래 연결 문서](payment-gateway-financial-links.md)에 정리했다.

이후 사용자 요청으로 자바 String / DB VARCHAR(36)으로 전환하고 UUID 생성·조회 호환성 검증을 추가해 총 156개(실제 PostgreSQL 12개 포함)가 통과했다. 문자열 전환 SQL은 기존 ID와 NULL을 보존하는 별도 데이터 검증 후 실제 DB에도 적용했다.

이어 신규 ID를 한국 날짜의 yyyyMMdd + UUID로 발급하도록 변경하고 DB를 VARCHAR(44)로 확장했다. 기존 36자 UUID 조회와 잘못된 날짜/UUID 검증을 포함해 최종 157개(실제 PostgreSQL 12개 포함)가 모두 통과했다. 확장 SQL의 기존 데이터 보존과 신규 schema.sql 실행도 별도 검증 스키마에서 확인하고 롤백했다.

2026-11-06 모의 은행·카드 HTTP 경계 변경 후에는 172개(실제 loopback HTTP 13개, 실제 PostgreSQL 12개 포함)가 모두 통과했다. 상세 구조와 한계는 [금융 HTTP 구현](payment-gateway-financial-http.md)에 정리했다.

2026-11-08 PG 가상계좌 발급 연결 후에는 같은 PG 선택 범위 189개(신규 발급 통합 16개, 기존 금융 HTTP 13개, 실제 PostgreSQL 13개 포함)가 모두 통과했다. 금융/PG 저장 실패와 HTTP 응답 유실의 복구 및 기존 API 범위 격리는 [PG 계좌 발급 문서](payment-gateway-account-issuance.md)에 정리했다. 실제 DB 스키마는 변경하지 않았으며 검증 데이터는 남기지 않았다.

## DB 스키마 대조와 일회성 마이그레이션 정리 (2026-11-11)

실행 중인 local-postgres 컨테이너의 ticket DB에 대해 두 서비스 schema.sql의 44개 테이블·481개 컬럼·119개 제약조건·61개 명시 인덱스를 읽기 전용으로 대조했다. 컬럼 타입/길이/NULL/기본값, 키 및 CHECK 제약의 정의·검증 상태, 인덱스 정의가 일치했다. DB 전체 public 테이블은 61개이며 다른 서비스 테이블은 이번 정리 범위에 포함하지 않는다.

제거된 과거 은행 상태·입금 이력 타입, 기존 미연결 계좌의 누락 입금 Outbox, ticket_count와 유효 티켓 수의 불일치는 모두 0건이었다. PG 4개·Ticksy 3개, 총 7개의 add-*.sql 파일을 삭제하고 최신 정의는 각 서비스 schema.sql에 남겼다. DB DDL·DML은 실행하지 않았으며 기존 테이블 삭제·초기화·데이터 변경은 없다. 다른 기존 DB까지 적용됐다는 의미는 아니므로 별도 환경은 해당 DB의 현재 상태에 맞춰 변경을 준비한다.
