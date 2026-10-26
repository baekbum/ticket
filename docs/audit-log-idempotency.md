# 감사 로그 멱등성

AUTH·USER·TICKET의 감사 Aspect는 감사 이벤트를 만들 때 `yyyyMMdd-UUID` 형식의 `idempotencyKey`를 한 번 발급한다. 날짜는 이벤트의 `occurredAt`과 같은 시각을 기준으로 한다. 내용이 같아도 별도 작업이면 새 키를 사용한다. Kafka 재전달·원본 DLT 재발행에서는 payload에 들어 있는 키를 그대로 사용하며, 프로듀서 전송이나 소비 시 새 키를 만들지 않는다.

감사 DB의 `audit_log`에 `idempotency_key`, `request_hash`를 저장하고 `(service_name, idempotency_key)`에 UNIQUE 제약을 둔다. 저장은 같은 트랜잭션 안에서 `INSERT ... ON CONFLICT (service_name, idempotency_key) DO NOTHING`으로 처리한다. 동시에 처음 들어온 요청은 DB의 UNIQUE 검사에서 선행 트랜잭션 종료를 기다린다. 선행 요청이 커밋되면 중복으로 처리하고, 롤백되면 대기하던 요청이 새 행을 저장할 수 있다.

충돌한 경우에는 기존 행의 해시를 비교한다. 동일 내용은 `DUPLICATE`로 정상 소비하고, 내용이 다르면 오류를 전파한다. 다른 DB 제약 위반·연결 장애는 중복으로 취급하지 않는다. 감사 로그 자체와 멱등성 정보가 같은 행에 있으므로 DB 저장이 롤백되면 재시도할 수 있다.

해시는 이벤트의 모든 DTO 필드를 대상으로 계산한다. JSON 객체는 중첩 키까지 정렬하고 숫자의 불필요한 소수점 차이는 정규화한다. 배열 순서, null, 빈 객체와 실제 값의 차이는 보존한다. DB의 자동 생성 ID·저장 시각은 이벤트 내용에 포함하지 않는다. 서로 다른 서비스는 같은 키를 사용할 수 있다.

이미 저장된 키로 DLT payload를 보정하여 다른 내용을 넣으면 거부한다. 최초 저장에 실패했던 이벤트는 기존 행이 없으므로 보정한 내용과 기존 키로 저장할 수 있다. 키가 없는 이벤트는 소비 시 키를 만들어 우회하지 않고 잘못된 이벤트로 처리한다. 배포 전 기존 토픽·DLT·수정 재발행 payload의 키를 확인해야 한다.

테이블 정의는 `audit-service/src/main/resources/sql/schema.sql`에 반영한다. 이 파일은 기존 감사·로그인 로그 테이블을 삭제하는 전체 초기화 스크립트이므로 기존 데이터를 보존할 DB에서 그대로 실행하지 않는다. 기존 데이터를 유지한다면 별도 키·해시 보정과 제약 적용이 필요하다. 소비자 배포 전 DB 스키마를 맞추고, 발행 서비스를 함께 갱신한다.

이 변경은 Kafka에 이미 도착한 감사 이벤트의 중복 저장을 방지한다. 발행 실패나 서버 종료로 이벤트 자체가 Kafka에 도착하지 않는 문제를 복구하는 Outbox는 포함하지 않는다.

## 검증

`AuditLogIdempotencyIntegrationTest`는 `AUDIT_IDEMPOTENCY_POSTGRES_PORT` 환경 변수를 지정하여 전용 PostgreSQL `audit_idempotency_test`(테스트 계정·비밀번호 `audit_test`)에서 실행한다. 테스트가 테이블을 생성·삭제하므로 임시 검증 DB에만 연결한다. 환경 변수가 없으면 해당 PostgreSQL 테스트는 건너뛴다.

검증 범위는 일반·동시 중복, 선행 요청 커밋·롤백, 다른 서비스·다른 키, 키 누락, 같은 키의 내용 변경, 다른 제약 위반, JSON 직렬화·역직렬화와 해시 정규화다.
