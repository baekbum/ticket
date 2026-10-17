# 환불 요청 멱등성

ticket-service의 `paymentRefundProcessId`를 카드/가상계좌 PG 환불 요청에 전달한다. 신규 환불 프로세스는 PG 호출 전에 `REQUIRES_NEW` 트랜잭션으로 커밋하며, 실패 재시도는 기존 프로세스 ID를 재사용한다.

PG의 `gateway_refunds`는 성공한 환불과 원래 응답을 저장한다. 프로세스 ID가 기본 키이며, 같은 ID로 결제번호·결제 수단·금액·카드 거래번호 또는 환불 계좌 정보가 달라지면 거부한다. 같은 요청의 재전달에는 원래 성공 응답을 반환한다. 금액의 소수점 표현 차이(4000과 4000.00)는 같은 금액으로 취급한다.

요청 변경 시 `POST /api/v1/payments/refunds/internal/resolve`를 사용한다. 이 API는 내부 서비스 토큰으로만 접근한다. 환불 실행과 같은 결제 행을 잠근 뒤 결과를 확인하며, `closeIfUnexecuted=true`이면 아직 실행되지 않은 ID에 `closed=true` 종료 기록을 저장한다. 종료한 ID의 늦은 환불 요청은 거부하고, 성공한 ID의 최초 결과는 유지한다. 종료 기록은 실제 환불액 합계에 포함하지 않는다. 조회만 수행할 경우 `SUCCEEDED`, `CLOSED`, `NOT_FOUND`를 반환한다.

신규 요청은 카드 승인 이력 또는 가상계좌 행을 잠근 후 프로세스 ID를 다시 조회한다. 동일 결제의 동시 요청은 잠금으로 직렬화한다. 환불 반영과 `gateway_refunds` 저장은 하나의 PG DB 트랜잭션으로 커밋한다. 실패 시 모두 롤백하고 같은 ID로 다시 시도할 수 있다. 서로 다른 결제가 같은 ID를 사용하려는 경우에도 기본 키 제약으로 중복 반영을 막는다.

카드는 기존 누적 환불액을 변경한다. 가상계좌는 잠금 아래 `gateway_refunds`의 성공 환불액 합계로 남은 환불액을 검증하며 기존 환불 이력도 같은 트랜잭션에 저장한다. 현재 PG는 더미 구현이므로 DB 반영이 환불 실행이다. 실제 외부 PG API를 연결할 때에는 외부 PG에도 같은 멱등 키를 전달하고 결과 조회/복구 흐름을 추가해야 한다.

## 배포

이번 변경을 기존 PG DB에 적용할 때는 관련 쓰기를 중단하고 다음 SQL을 먼저 실행한다.

```sql
ALTER TABLE gateway_refunds
    ADD COLUMN IF NOT EXISTS closed BOOLEAN NOT NULL DEFAULT FALSE;
```

1. 기존 PG DB에는 `payment-gateway-service/src/main/resources/sql/add-gateway-refunds.sql`을 적용한다. 초기화용 `schema.sql` 전체를 기존 DB에 실행하지 않는다.
2. ticket과 PG를 함께 갱신한다. 새 PG는 프로세스 ID 없는 환불 요청을 거부하므로 구버전 ticket에서 새 PG로 환불 요청이 들어가지 않도록 환불을 잠시 중단하거나 배포를 조율한다.
3. 배포 이전에 이미 실행된 환불에는 이 테이블의 기록이 없다. 과거 PG 성공 여부가 불명확한 프로세스를 자동 재시도하지 말고 PG 이력과 대조한다. 가상계좌 누적 한도 검증에도 과거 환불액은 자동 포함되지 않으므로 기존 환불 건은 별도로 정합성을 확인해야 한다.

## 검증

`GatewayRefundIntegrationTest`는 H2 DB에서 카드/가상계좌 재전달, 요청 불일치, 기본 키 중복 삽입 거부, 누적 환불 한도, DB 롤백, 동시 요청을 검증한다. 기존 환불 및 사용자/관리자 취소 테스트는 프로세스 ID 전달 경로를 검증한다. 실제 PostgreSQL 환경에서의 장애 재현과 배포는 별도로 수행한다.
