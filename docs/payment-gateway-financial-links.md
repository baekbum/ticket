# PG 거래와 금융 모델 연결

2026-11-05 구현한 첫 번째 금융 연결 단계다. 모델과 DB에 PG 참조를 추가하며, 기존 카드 승인/가상계좌 발급 API를 PG 거래에 연결하는 두 번째 단계와 구분한다.

2026-11-08 [가맹점 PG 거래 기반 가상계좌 발급](payment-gateway-account-issuance.md)을 추가했다. 새 경로의 발급에는 이 문서의 PG 참조를 실제 저장하며, 기존 금융 API는 NULL 참조 거래를 계속 처리한다. 새 은행 연결 거래의 입금·취소·환불·만료·통보는 후속 단계다. 같은 날 [PG 카드 승인](payment-gateway-card-approval.md)도 연결했으며 기존 카드의 NULL 연결 거래는 유지한다.

## 모델과 책임

| 모델/테이블 | 연결 |
| --- | --- |
| `DummyCard` / `dummy_cards` | 카드 원장. PG 식별자를 추가하지 않는다. |
| `DummyCardPaymentHistory` / `dummy_card_payment_histories` | 원승인 거래에 `pg_payment_id VARCHAR(44)` 추가. 하나의 PG 거래에는 카드 거래 행이 최대 하나다. |
| `DummyVirtualAccount` / `dummy_virtual_accounts` | 계좌에 `pg_payment_id VARCHAR(44)` 추가. 하나의 PG 거래에는 계좌 행이 최대 하나다. |
| `DummyVirtualAccountPaymentHistory` | 기존 계좌 FK를 통해 PG 거래에 연결된다. PG ID를 중복 저장하지 않는다. |

하나의 카드 원장에 서로 다른 PG ID의 원승인 거래를 여러 개 연결할 수 있다. 카드의 월 사용액·한도와 원승인 거래의 금액·환불 누적액은 기존 모델이 계속 관리한다.

금융 모델은 `GatewayPayment` 엔티티 대신 `yyyyMMdd + UUID` 형식의 String 식별자만 받는다. 기존 36자 UUID 참조도 보존한다. PG가 가맹점 인증, 거래 소유권, 저장된 수단·금액·은행 등을 확인한 뒤 내부 생성 메서드에 전달하는 구조다. 식별자 필드만 추가했다고 이러한 업무 검증까지 적용된 것으로 해석하지 않는다.

다음 생성 메서드를 추가했다. 기존 매개변수 목록 앞에 PG 문자열 식별자를 받는 오버로드다.

- `DummyCardPaymentHistory.approved(String pgPaymentId, ...)`
- `DummyCardPaymentHistory.approvalFailed(String pgPaymentId, ...)`
- `DummyVirtualAccount.issue(String pgPaymentId, ...)`

새 생성 메서드는 null 식별자를 거절한다. 저장 후 연결을 바꾸는 setter는 제공하지 않으며 JPA 컬럼은 `updatable = false`다. 승인 실패 행도 같은 PG 거래를 참조할 수 있다. 이는 작업 시도마다 별도 원승인 행을 만드는 변경이 아니며, 재승인 정책은 기존 행을 기준으로 후속 단계에서 정리한다.

## 내부 조회

- 카드 거래: `findByPgPaymentId(String)`
- 가상계좌: `findByPgPaymentId(String)`
- 입금 계좌번호에서 PG 문자열 ID만 조회: `findPgPaymentIdByAccountNumber(String)` → `Optional<String>`

계좌번호 조회는 금융 행을 잠그지 않고 문자열 ID만 가져온다. 후속 입금 서비스에서 PG 거래 → 계좌 순서로 잠그고 연결과 상태를 재확인하기 위한 조회다. 아직 기존 입금 서비스의 잠금 순서를 바꾸지는 않았다.

금융 리포지토리의 PG ID 조회는 내부 용도다. 외부 요청은 인증된 가맹점 범위로 `GatewayPayment`를 확인한 후에만 금융 거래를 찾아야 한다. 식별자를 안다는 사실만으로 접근 권한이 생기지 않는다.

## 스키마와 호환성

현재 테이블 정의의 기준은 [schema.sql](../payment-gateway-service/src/main/resources/sql/schema.sql)이다. 금융 연결·문자열 전환·길이 확장에 사용한 개별 SQL 3개는 실제 DB 적용을 마친 후 2026-11-06 사용자 요청으로 삭제했다. 신규 DB는 최신 schema.sql을 사용하고, 기존 DB는 실제 테이블 정의와 비교해 차이가 있는 대상만 처리한다.

두 금융 테이블에 각각 다음을 추가한다.

- nullable VARCHAR(44) 컬럼 `pg_payment_id` (자바 String)
- `gateway_payments(pg_payment_id)`를 참조하는 FK
- 같은 테이블에서 PG ID의 중복 연결을 막는 UNIQUE

NULL은 기존 API와 아직 검증하지 않은 과거 데이터의 이행 상태다. PostgreSQL UNIQUE는 여러 NULL을 허용하므로 기존 결제가 여러 건이어도 충돌하지 않는다. 값이 있는 연결은 실제 PG 거래를 참조해야 하며 같은 금융 테이블에서 중복될 수 없다. 카드 원장의 스키마는 변경하지 않는다.

schema.sql은 CREATE TABLE/INDEX 정의이며 기존 테이블을 자동 삭제하거나 변경하지 않는다. 기존 DB를 재생성할 때는 데이터와 참조 관계를 확인하고 대상 DDL과 외래키 복원을 한 트랜잭션에서 처리한다.

문자열 스칼라 필드의 FK는 schema.sql에 정의한다. Hibernate 자동 스키마 갱신만으로 이 FK가 생성된다고 가정하면 안 된다. 배포할 때 최종 스키마의 FK 정의도 확인해야 한다.

기존 API, 요청/응답 DTO, 기존 생성 메서드는 그대로 유지한다. 따라서 현재 서비스가 생성하는 금융 행은 계속 NULL로 저장된다. PG ID가 담긴 결제를 실제 요청 흐름에서 생성하도록 전환하는 작업은 다음 단계다. PG 상태, 금융 상태, Outbox와 통보 흐름도 이번에는 변경하지 않는다.

현재 금융 테이블의 전역 `payment_no` 유니크와 기존 조회도 유지한다. 여러 가맹점의 동일 번호로 **실제 금융 처리까지** 가능해졌다는 의미는 아니다. 기존 조회를 PG ID/가맹점 범위로 바꾸기 전에 이 유니크만 먼저 제거하지 않는다.

## 기존 데이터의 처리

이번 모델 연결은 어떤 금융 행도 특정 가맹점으로 자동 귀속하지 않는다. 기존 paymentNo가 일치하거나 가맹점 표시 이름이 같다는 이유만으로 연결하면 안 된다.

후속 이관 순서는 다음과 같다.

1. 서비스 운영 근거로 가맹점, 예매자, 결제수단, 금액, 원승인/계좌를 대사한다. 카드/계좌의 동일 번호 충돌도 확인한다.
2. 확인된 거래만 해당 merchant의 `GatewayPayment`에 연결한다. 이미 발급한 PG ID와 최초 요청/처리 이력은 재실행에서도 재사용한다. 기존 금융 상태를 PG 상태로 옮기는 규칙은 전체 목표 설계를 따른다.
3. 금융 행의 `pg_payment_id`를 승인된 이관 SQL로 채운다. 연결 컬럼은 JPA에서 수정 불가이므로 일반 서비스의 엔티티 수정으로 과거 행을 이관하지 않는다.
4. 가맹점을 식별할 수 없는 행과 불일치는 NULL로 남겨 대사 대상으로 관리한다. 신규 다중 가맹점 경로가 미확인 행을 처리하지 않도록 차단한다.
5. API 진입점, 조회·취소·환불, Outbox까지 전환하고 모든 금융 행의 소유권을 확인한 뒤 NOT NULL과 최종 조회/유니크 정책을 적용한다. 미확인 행이 남으면 NOT NULL 적용을 미룬다.

## 실제 DB 적용

사용자의 기존 DB 적용 요청에 따라 2026-11-05 `local-postgres`의 `ticket` DB에 적용했다. 이 DB에는 다시 실행하지 않는다.

이후 같은 날 사용자의 명시적인 삭제·재생성 요청으로 변경 테이블인 `dummy_card_payment_histories`, `dummy_virtual_accounts` 두 개를 최신 `schema.sql` 정의로 다시 생성했다. 잠금 안에서 대상과 계좌 이력이 모두 0건임을 재확인했다. 계좌 이력 테이블은 유지하고 참조 FK만 같은 트랜잭션에서 해제·복원했으며, 두 테이블의 컬럼·인덱스·제약과 ID 시퀀스를 다시 생성했다. 기존 데이터가 있는 환경에서 반복 실행할 일반 마이그레이션은 아니다.

재생성 후 `GatewayCorePostgresTest` 12개를 다시 실행해 실패/오류/건너뜀 없이 통과했다. 대상·계좌 이력·PG 거래·PG 이력은 0건, 기존 가맹점은 1건으로 최종 확인했다.

이후 사용자의 String 타입 요청으로 `pgPaymentId`를 자바 String / DB VARCHAR(36)으로 통일했다. UUID 생성은 유지하며 당시 문자열 변환 작업을 실제 DB에 적용했다. PG 결제·PG 이력·카드 거래·가상계좌 네 컬럼을 같은 트랜잭션에서 바꾸고 세 FK를 복원했다. 별도 검증 스키마에 연결된 거래와 NULL 거래를 넣어 변환 전후 모든 행의 JSON 스냅샷이 같은지 검증한 뒤 롤백했다. 실제 테이블이나 행은 삭제하지 않았다.

이후 날짜 접두사 요청에 따라 최초 저장 시 한국 날짜 8자리 + UUID 36자, 총 44자로 발급하도록 변경하고 네 참조 컬럼을 VARCHAR(44)로 확장했다. 확장 SQL도 별도 스키마의 연결된 기존 ID·NULL·전체 행이 유지되는지 검증한 뒤 실제 DB에 적용했다. 기존 식별자를 재발급하거나 기존 거래의 날짜를 추정해 채우지 않는다.

| 확인 항목 | 적용 전 → 적용 후 |
| --- | --- |
| 카드 승인·가상계좌의 PG 식별자 컬럼 | 0개 → 2개, 둘 다 nullable VARCHAR(44) |
| 새 PG 참조 FK / UNIQUE | 각각 0개 → 2개 |
| 카드 원장의 PG 식별자 컬럼 | 0개 → 0개 |
| 카드 원장 / 카드 거래 / 가상계좌 / PG 거래 | 각각 0건 → 0건 |
| 가맹점 | 1건 → 1건 |

2026-11-06 개별 SQL 3개 삭제 후 최종 schema.sql을 별도 비교 스키마에 생성해 실제 PG 테이블 10개와 비교했다. 컬럼명·순서·타입/길이/정밀도·NULL·기본값·제약 이름/정의·인덱스 이름/정의가 전부 일치했다. 삭제·재생성 대상은 0개였으며 실제 테이블은 변경하지 않았다. 가맹점 1건과 나머지 PG 테이블의 0건 상태를 유지했고 비교 스키마는 롤백했다.

## 검증

`GatewayFinancialLinkIntegrationTest`는 격리 H2에서 연결 저장·재조회, 계좌 이력을 통한 연결, 카드 하나의 여러 PG 결제, 중복 연결 거절, 여러 NULL 거래의 호환성, 승인 실패 연결, 명시적 PG ID 필수값, PG 이력 저장 실패 시 PG/금융 행 전체 롤백을 검증한다.

`GatewayCorePostgresTest`는 실제 DB의 nullable VARCHAR(44)·FK·유니크와 카드 원장 미변경, 날짜+UUID 문자열 생성 및 연결 저장·조회, 실제 FK의 없는 PG ID 거절, 실제 유니크의 중복 연결 거절, 여러 기존 NULL 거래 저장을 추가 검증한다. DDL은 `validate`만 사용하고 각 테스트의 데이터는 롤백한다. H2의 자동 생성 DDL만으로 문자열 스칼라 FK를 검증하지 않는다.

2026-11-05 다음 범위를 실행해 `BUILD SUCCESSFUL`을 확인했다. 기존 카드·가상계좌·취소/입금 경합·Outbox·환불 회귀를 포함한다.

```powershell
.\gradlew.bat :payment-gateway-service:test `
  --tests '*.service.*' --tests '*.security.*' --tests '*.config.*' --tests '*.jpa.gateway.*' `
  --offline --no-daemon --console=plain
```

실제 DB 테스트를 포함하려면 기반 구현 문서의 `GATEWAY_CORE_VERIFY_URL/USER/PASSWORD`를 설정한다. 기존 `GATEWAY_TEST_POSTGRES_PORT`, `GATEWAY_DEPOSIT_TEST_POSTGRES_PORT`는 설정하지 않고 해당 테스트는 H2로 실행했다.

| 실행 범위 | 테스트 | 실패/오류/건너뜀 |
| --- | --- | --- |
| PG 전체 선택 범위, 19개 테스트 클래스 | 157 | 각 0 |
| 위 범위 중 실제 PostgreSQL 검증 | 12 | 각 0 |
| 위 범위 중 신규 금융 연결 H2 검증 | 9 | 각 0 |

날짜 접두사 추가 후 다시 실행한 최종 결과다. 가맹점 통합 테스트에서 한국 날짜+UUID 자동 생성, 대문자 ID 정규화 조회, 잘못된 날짜·UUID의 400 응답, 기존 36자 UUID 조회 및 가맹점 접근 범위를 검증했다. DB에서도 네 VARCHAR(44) 컬럼과 세 PG 참조 FK가 유지되며 테스트 데이터와 검증 스키마가 남지 않았음을 확인했다.

신규 DB용 `schema.sql` 전체도 별도 검증 스키마에서 트랜잭션으로 실행해 금융 연결 컬럼과 네 제약을 확인하고 롤백했다. 종료 후 검증 스키마는 0개다. 실제 public 테이블 수는 55개, 가맹점은 기존 1건이며 금융/PG 거래와 검증용 데이터는 0건으로 유지됐다.

2026-11-06 외부 금융 API는 [RestClient → 모의 금융 컨트롤러](payment-gateway-financial-http.md)로 변경했다. 금융 요청 내부의 입금·이력·Outbox 원자성은 유지한다. 실제 PG 상태 연결은 HTTP 전후의 별도 PG 트랜잭션과 결과 조회/복구로 다루며 HTTP를 가로지르는 로컬 원자성으로 취급하지 않는다.
