# PG 가맹점·거래 기반 구현

이 문서는 [전체 목표 설계](payment-gateway-domain-design.md) 중 실제 적용한 기반 기능을 설명한다. 금융 거래 연결과 카드 결제 세션은 후속 단계다.

## 적용 범위

- `Merchant`, `GatewayPayment`, `GatewayPaymentHistory`와 가맹점 범위 리포지토리를 추가했다.
- `merchants.merchant_id`가 숫자 가맹점 코드이며 가맹점명은 중복을 허용한다.
- `gateway_payments`가 `(merchant_id, merchant_payment_no)`를 UUID PG 식별자에 매핑한다. 매핑 전용 테이블은 없다.
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

| 결과 | 상태 |
| --- | --- |
| 준비/동일 요청/본인 merchant 거래 조회 | 200 |
| 입력·수단·기한·유효 금액 오류 | 400 |
| 토큰 누락·불일치·DB에 미등록된 자격증명 그룹 | 401 |
| 본문 merchantId 위변조 또는 중단 merchant의 신규 준비 | 403 |
| 다른 merchant 거래 또는 없는 거래 | 404 |
| 동일 번호의 다른 준비 내용 | 409 |
| 가맹점 인증 DB를 조회할 수 없음 | 503 |

SUSPENDED merchant는 새 준비를 할 수 없고 기존 거래는 조회할 수 있다. 금융 취소·환불의 상태 정책은 해당 연결 단계에서 적용한다.

## 인증 설정과 기존 API 호환성

기본 `app.merchants.credentials.ticksy` 그룹은 기존 `app.ticket.service-token`을 재사용한다. 운영에서는 기존과 같이 `APP_INTERNAL_SERVICE_TOKEN`을 주입한다. 원문 토큰은 DB에 넣지 않고 `credential_key = ticksy`로 merchant를 등록한다.

가맹점 추가 시 서버 설정과 DB 등록을 둘 다 수행한다. 예를 들어 별도 환경 설정에서 다음과 같이 주입할 수 있다. 실제 비밀값을 커밋하지 않는다.

```yaml
app:
  merchants:
    credentials:
      melon:
        - ${APP_MELON_SERVICE_TOKEN}
```

같은 그룹의 리스트에 이전 토큰을 추가하면 기존 요청을 위한 회전 기간을 둘 수 있다. 그룹 사이 동일 토큰 등록은 시작 시 거절한다. credential_key는 유니크이며 merchant_name과 무관하다. 숫자 코드가 1이라는 이유로 Ticksy로 분기하지 않는다.

**기존 금융 API는 아직 다중 가맹점으로 전환하지 않았다.** 기존 카드 사용자 JWT 경로와 Ticksy 내부 서비스 토큰 경로는 기존 계약을 유지한다. 다른 merchant의 새 토큰은 기존 발급·취소·환불·resolve API에 접근할 수 없다. 신규 ROLE_MERCHANT를 기존 ROLE_INTERNAL_SERVICE와 혼용하지 않는다. 기존 카드·계좌·환불 행에 merchant를 추정해 연결하지 않았다.

이번 prepare 응답에는 카드 결제 세션 토큰이 없다. 기존 프런트엔드가 이 준비 거래를 승인에 사용하도록 연결하거나 새 가맹점의 실제 금융 처리를 활성화하는 것은 후속 작업이다. 기존 결제의 사용자 소유자 검증은 유지한다.

## 스키마와 DB 적용

- 신규 DB: [schema.sql](../payment-gateway-service/src/main/resources/sql/schema.sql)과 [initData.sql](../payment-gateway-service/src/main/resources/sql/initData.sql).
- 기존 DB: [add-merchants-gateway-payments.sql](../payment-gateway-service/src/main/resources/sql/add-merchants-gateway-payments.sql).
- 설계 기록: [DDL 초안](design/payment-gateway-core-schema.sql). 실행/배포 기준은 위 리소스 SQL이다.

마이그레이션은 BEGIN/COMMIT과 트랜잭션 advisory lock을 사용한다. 새 테이블 3개·인덱스·FK/체크/유니크 제약을 생성하고 비밀값 없는 Ticksy 등록 1건을 추가한다. 기존 테이블 변경·삭제·초기화나 기존 금융 데이터 이전은 포함하지 않는다. 이미 테이블이 있으면 자동 무시하지 않고 오류로 롤백한다.

```powershell
Get-Content -Raw -Encoding UTF8 payment-gateway-service/src/main/resources/sql/add-merchants-gateway-payments.sql |
  docker exec -i local-postgres psql -U postgres -d ticket -v ON_ERROR_STOP=1
```

사용자 요청에 따라 2026-11-04 `local-postgres`의 `ticket` DB에 적용했다. **이 DB에서 다시 실행할 필요가 없다.** 적용 시 Ticksy의 숫자 코드는 DB 시퀀스로 발급하며 고정 번호를 코드에 넣지 않는다. 최종 데이터 이전 SQL은 원거래·환불·Outbox를 실제 연결하는 단계에서 별도로 추가한다.

적용 후 실제 DB 조회 결과는 다음과 같다.

| 확인 항목 | 결과 |
| --- | --- |
| public 테이블 수 | 기존 52개 → 55개 |
| 가맹점 등록 | merchant_id=1, Ticksy, ACTIVE, credential_key=ticksy |
| PG 거래 / PG 이력 | 각 0건. PostgreSQL 검증용 데이터가 남지 않음 |
| 기존 payments·카드 원장/승인·계좌/은행 이력·환불 | 적용 전후 각 0건으로 동일 |
| 신규 테이블 PK·FK·유니크 제약 | 9개 확인. 금액·상태 등의 CHECK도 마이그레이션으로 생성 |

## 검증 방법

`GatewayMerchantIntegrationTest`는 격리된 H2에서 실제 Spring Security 필터·MVC·서비스·JPA를 실행한다. 중복 이름/번호, 교차 조회, 본문 위변조, 인증 종류, 토큰 회전, 가맹점 중단, 동시 요청과 금액 충돌, 등록 이력 실패의 전체 롤백을 확인한다. `MerchantCredentialResolverTest`는 설정 중복과 유효하지 않은 토큰을 확인한다.

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
