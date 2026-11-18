# DB 기반 PG 가맹점 인증

> 2026-11-18: 구형 paymentNo 전용 PG/금융 API와 X-Service-Token 전용 PG 인증은 폐쇄했다. 아래의 구형 경로 설명은 이전 구조 기록이며 호출 계약으로 사용하지 않는다. 현재 변경 범위와 보존한 API는 [레거시 정리 기록](payment-legacy-retirement.md)을 따른다. 은행 입금은 `/api/v1/banks/virtual-accounts/deposit`, 환불은 신규 PG 거래·가맹점 범위 API를 사용한다.

2026-11-13부터 신규 가맹점 거래 API는 두 헤더를 사용한다.

```http
Merchant: 1
credentialKey: ticksy_{PG가 발급한 무작위 비밀값}
```

`Merchant`는 숫자 가맹점 코드이며 조회 대상이다. `credentialKey`는 실제 인증 수단이다. 키 접두사는 표시 용도이며 파싱해서 가맹점을 선택하거나 가맹점명과 비교하지 않는다. 같은 표시 이름이나 같은 접두사를 가진 가맹점도 서로 다른 코드와 키를 사용한다.

## 발급과 저장

- 기존 `merchants` 테이블의 `credential_key` 설정 그룹 참조를 `credential_hash VARCHAR(64)`로 전환했다. 별도 자격증명 테이블은 추가하지 않는다.
- PG는 `MerchantCredentialKey.generate(prefix)`로 `prefix_무작위값`을 생성한다. 접두사는 영문 소문자로 시작하는 영문·숫자·하이픈 1~63자이며 비밀값은 SecureRandom 32바이트를 Base64 URL 형식으로 인코딩한 43자다.
- PG는 원문 전체의 SHA-256 해시만 저장한다. UNIQUE 제약은 서로 다른 가맹점이 같은 키를 갖지 못하게 한다. 원문은 DB·소스·초기 SQL·로그에 넣지 않는다.
- `credential_hash = NULL`은 미발급 또는 폐기 상태다. 가맹점이 ACTIVE여도 인증을 거절한다.
- 가맹점마다 사용 가능한 키는 하나다. 해시를 교체하면 이전 키는 다음 요청부터 거절된다. 여러 키의 동시 사용이나 교체 유예 기간은 지원하지 않는다.

## PG 운영자 발급 명령

공개 발급 HTTP API는 추가하지 않았다. PG 서버 운영자가 DB 접속 권한으로 전용 명령을 실행한다. DB 접속 정보는 다음 환경변수로만 제공한다.

```text
PG_CREDENTIAL_DB_URL
PG_CREDENTIAL_DB_USER
PG_CREDENTIAL_DB_PASSWORD
```

저장소 루트에서 실행하는 예:

```powershell
.\gradlew.bat :payment-gateway-service:issueMerchantCredential `
  -PmerchantId=1 -PmerchantPrefix=ticksy `
  -PcredentialOutput=D:/ticket/ticket/ticket-service/pg-merchant.properties
```

명령은 해당 가맹점을 잠그고 새 키 해시를 갱신한 뒤, 원문 키를 새로운 설정 파일에 한 번 기록한다. 발급 대상 가맹점은 사전에 등록되어 있어야 한다. 기존 파일 덮어쓰기는 거절하므로 키 교체 시 새 출력 경로를 지정하고 관리자가 Ticksy 설정을 교체한다. DB 커밋 응답이 유실되면 결과가 불확실할 수 있으므로 생성된 파일을 삭제하지 않으며, 운영자가 파일 키의 해시와 DB를 확인한다.

생성 파일의 속성은 `app.payment-gateway.merchant-id`, `app.payment-gateway.credential-key`다. `pg-merchant.properties`는 Git에서 제외하며 로컬 Ticksy가 저장소 루트 또는 서비스 디렉터리에서 실행될 때 선택적으로 읽는다. 파일을 공유 저장소에 업로드하지 않는다. 다른 출력 이름은 운영자가 직접 비밀 설정 저장소로 전달한다.

배포 환경의 Ticksy에는 `APP_PG_MERCHANT_ID`, `APP_PG_CREDENTIAL_KEY`를 주입한다. 설정이 없으면 신규 PG 호출은 인증 설정 오류로 중단하며 내부 서비스 토큰으로 대체하지 않는다. 실행 중인 Ticksy의 설정을 교체했다면 해당 프로세스를 재시작해야 한다.

## 요청 인증

1. `MerchantAuthenticationFilter`가 각각 하나인 `Merchant`, `credentialKey` 헤더를 요구한다. 누락·중복 헤더는 거절한다.
2. `MerchantAuthenticationService`가 양수 Long 형식의 코드를 확인하고 PK로 가맹점 한 행을 조회한다.
3. 요청 키를 해시해서 저장된 해시와 일정 시간 비교한다. 없는 가맹점, 미발급 키, 다른 가맹점 키는 모두 같은 401 응답이다. DB 장애는 기존 계약대로 503이다.
4. `AuthenticatedMerchant(merchantId)`만 SecurityContext에 보관한다. 원문 키나 해시를 컨트롤러·응답·Outbox에 전달하지 않는다.
5. 기존 서비스가 본문 가맹점 코드와 거래 소유 관계를 다시 확인한다. SUSPENDED는 기존 조회·정리를 허용하되 신규 준비·승인·발급을 차단한다.

## 호출부와 호환성

- `PaymentGatewayTransactionClient`는 `PaymentGatewayMerchantFeignConfig`로 두 헤더를 설정한다. prepare·조회·발급·취소의 URL과 DTO는 유지한다.
- PG 거래 API의 기존 `X-Service-Token` 단독 인증은 제거했다. 호출자는 새 헤더로 전환해야 한다.
- 레거시 카드·은행·환불 Feign은 기존 `PaymentGatewayFeignConfig`와 `X-Service-Token` 계약을 유지한다. PG→Ticksy 통보도 등록된 통보 대상의 별도 인증값을 계속 사용한다.
- PG의 `app.merchants.credentials` YAML 목록과 `MerchantCredentialProperties`, `MerchantCredentialResolver`는 제거했다. `app.ticket.service-token`은 다른 내부 호출과 기존 통보 암호화 설정에 쓰이므로 유지한다.
- `initData.sql`은 비밀값 없이 숫자 코드 1의 Ticksy를 미발급 상태로 등록한다. 이미 존재하는 가맹점의 해시를 덮어쓰지 않는다. 가맹점 이름은 중복을 허용한다.

## 기존 DB 전환

실제 `local-postgres/ticket`은 테이블 삭제 없이 컬럼·제약을 전환했다. 기존 설정 그룹 문자열은 인증 키가 아니므로 해시로 변환하지 않고 NULL로 바꾼 뒤 Ticksy 키를 새로 발급했다. 가맹점·결제·계좌·Outbox 행과 숫자 코드는 보존한다. 다른 DB도 기존 각 가맹점의 관리자를 확인한 후 새 키를 발급해야 하며, 식별할 수 없는 가맹점에는 임의로 키를 연결하지 않는다. 초기 SQL 전체를 기존 DB에 다시 실행하지 않는다.

## 검증

실제 필터·MVC·JPA 통합 테스트는 헤더 누락·중복·코드 위조·타 가맹점 키·본문 위조·이전 서비스 토큰 거절·키 교체·폐기를 검증한다. 기존 카드 승인·은행 발급·입금·취소·만료 테스트도 새 헤더로 실행한다. 기존 DB 대상 GatewayCorePostgresTest는 DDL을 validate만 하고 모든 테스트 데이터를 롤백하며 nullable 해시와 UNIQUE 제약을 확인한다. 테이블을 자동 생성·정리하는 checkout 재시도 검증은 분리된 임시 PostgreSQL에서 실행한 후 컨테이너를 정리했다.

최종 검증은 PG 341개와 Ticksy 264개, 총 605개가 모두 통과했으며 건너뛴 항목은 없다. 기존 DB에서 실행한 PG PostgreSQL 검증 26개도 포함한다. PG·Ticksy bootJar 빌드가 성공했고, 실제 DB와 두 schema.sql의 44개 테이블·492개 컬럼·120개 제약·63개 명시적 인덱스 비교에서 차이가 없었다. 발급 원문 키가 Git 변경 파일에 포함되지 않는 것도 확인했다.
