# 환불 요청 멱등성

ticket-service의 `paymentRefundProcessId`를 카드/가상계좌 PG 환불 요청에 전달한다. 신규 환불 프로세스는 PG 호출 전에 `REQUIRES_NEW` 트랜잭션으로 커밋하며, 실패 재시도는 기존 프로세스 ID를 재사용한다.

PG의 `gateway_refunds`는 최초 요청과 처리 상태(`REQUESTED`, `SUCCEEDED`, `FAILED`, `CLOSED`), 성공 응답과 실패 이유를 저장한다. 프로세스 ID가 기본 키이며, 같은 ID로 결제번호·결제 수단·금액·카드 거래번호 또는 환불 계좌 정보가 달라지면 거부한다. 이미 성공한 요청의 재전달에는 원래 성공 응답을 반환하고, 미완료·실패 요청은 같은 ID와 최초 조건으로 재시도한다. 금액의 소수점 표현 차이(4000과 4000.00)는 같은 금액으로 취급한다.

요청 변경 시 `POST /api/v1/payments/refunds/internal/resolve`를 사용한다. 이 API는 내부 서비스 토큰으로만 접근한다. 환불 실행과 같은 결제 행을 잠근 뒤 결과를 확인하며, `closeIfUnexecuted=true`이면 아직 성공하지 않은 ID를 `CLOSED`로 종료한다. 기록이 없는 ID도 종료 기록을 저장해 늦은 실행을 차단한다. 이미 성공한 ID의 최초 결과는 유지한다. 조회 결과는 `SUCCEEDED`, `CLOSED`, `REQUESTED`, `FAILED`, `NOT_FOUND`이며, 최초 기록만 존재한다고 환불 성공으로 간주하지 않는다.

PG의 A 트랜잭션은 카드 승인 이력 또는 가상계좌 행을 먼저 잠그고 프로세스 ID로 환불 기록을 일반 조회한다. 기록이 없으면 생성 전용 B(`REQUIRES_NEW`)가 요청 정보만 저장·커밋한다. B는 A가 잠근 결제 행을 조회하거나 갱신하지 않는다. A는 커밋된 요청을 일반 조회하고, 실제 환불과 `SUCCEEDED` 변경을 함께 커밋한다. 동일 결제의 동시 요청은 원래 결제 행의 잠금으로 직렬화하며, 환불 기록의 별도 비관적 락은 사용하지 않는다. 서로 다른 결제가 같은 ID를 사용하려는 경우에도 기본 키 제약으로 중복 생성을 막는다.

A가 롤백되면 최초 기록은 남고 실제 환불·성공 상태는 되돌아간다. 잠금 해제 후 별도 트랜잭션에서 같은 결제 행을 잠그고 실패 상태를 기록한다. 이미 성공·종료됐거나 버전이 변경된 기록은 과거 실패로 덮어쓰지 않는다. 실패 기록 자체가 유실돼도 `REQUESTED` 기록과 같은 프로세스 ID로 재시도할 수 있다. ticket도 `Reservation` 잠금 아래 프로세스를 일반 조회하고, 롤백 후 실패 기록은 먼저 예매를 잠근 뒤 수행한다.

카드는 기존 누적 환불액을 변경한다. 가상계좌는 잠금 아래 `gateway_refunds` 중 `SUCCEEDED`인 금액만 합산해 남은 환불액을 검증하며 기존 환불 이력도 같은 트랜잭션에 저장한다. 요청·실패·종료 기록은 환불액에 포함하지 않는다. 현재 PG는 더미 구현이므로 DB 반영이 환불 실행이다. 실제 외부 PG API를 연결할 때에는 외부 PG에도 같은 멱등 키를 전달하고 결과 조회/복구 흐름을 추가해야 한다.

ticket의 사용자 취소·관리자 취소·수동 환불 완료는 `Reservation`을 `FOR UPDATE NOWAIT`로 잠근다. 같은 예매가 다른 트랜잭션에서 처리 중이면 기다리지 않고 `409 Conflict`, `RESERVATION_CANCELLATION_IN_PROGRESS`로 응답한다. 먼저 시작한 요청은 계속 처리되며, 실패 후 명시적으로 다시 요청하면 기존 프로세스 ID와 스냅샷을 재사용한다. 롤백 후 실패 이력을 기록하는 내부 트랜잭션은 기존 대기형 잠금을 유지한다. PG의 결제 잠금도 기존 대기 방식을 유지한다.

## 배포

이전 `closed` 컬럼을 사용하는 PG DB를 갱신할 때는 관련 쓰기를 중단하고 아래 SQL을 한 번 실행한다. 기존 성공·종료 기록을 새 상태로 변환하며, 최초 요청 생성용 기본값은 변환 후 `REQUESTED`로 설정한다. 신규 DB는 갱신된 생성 SQL을 사용한다.

```sql
BEGIN;
ALTER TABLE gateway_refunds
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'SUCCEEDED',
    ADD COLUMN failure_reason VARCHAR(1000),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
UPDATE gateway_refunds SET status = CASE WHEN closed THEN 'CLOSED' ELSE 'SUCCEEDED' END;
ALTER TABLE gateway_refunds
    ALTER COLUMN status SET DEFAULT 'REQUESTED',
    DROP COLUMN closed,
    ADD CONSTRAINT chk_gateway_refunds_status CHECK (status IN ('REQUESTED', 'SUCCEEDED', 'FAILED', 'CLOSED'));
COMMIT;
```

1. `gateway_refunds`가 없는 DB에는 `payment-gateway-service/src/main/resources/sql/add-gateway-refunds.sql`을 적용한다. 기존 테이블은 위 상태 변환 SQL로 갱신한다. 초기화용 `schema.sql` 전체를 기존 DB에 실행하지 않는다.
2. ticket과 PG를 함께 갱신한다. 새 PG는 프로세스 ID 없는 환불 요청을 거부하므로 구버전 ticket에서 새 PG로 환불 요청이 들어가지 않도록 환불을 잠시 중단하거나 배포를 조율한다.
3. 배포 이전에 이미 실행된 환불에는 이 테이블의 기록이 없다. 과거 PG 성공 여부가 불명확한 프로세스를 자동 재시도하지 말고 PG 이력과 대조한다. 가상계좌 누적 한도 검증에도 과거 환불액은 자동 포함되지 않으므로 기존 환불 건은 별도로 정합성을 확인해야 한다.

## 검증

`GatewayRefundIntegrationTest`는 H2 DB에서 카드/가상계좌 재전달, 요청 불일치, 기본 키 중복 삽입 거부, 성공 금액만의 누적 한도, 최초 기록의 독립 커밋, 실제 환불 롤백과 같은 ID 재시도를 검증한다. 원래 결제 행을 잠근 동시 요청의 대기, 요청·실패·종료 조회 결과, 늦은 실패 기록의 성공 상태 덮어쓰기 방지도 검증한다. 기존 환불 및 사용자/관리자 취소 테스트는 프로세스 ID 전달 경로를 검증한다. 실제 PostgreSQL 환경에서의 장애 재현과 배포는 별도로 수행한다.
