# 회원 이벤트 Outbox

회원 생성·수정·탈퇴·삭제와 인증 정보 변경 이벤트를 USER DB의 같은 트랜잭션에 저장한다. `UserService`는 Kafka를 직접 호출하지 않고 `UserOutboxService.enqueue()`를 사용한다. 관리자 비밀번호 변경·초기화도 암호화된 비밀번호 이벤트를 기록한다.

```text
A: 회원 변경 → flush → user_outbox_events(PENDING) 저장 → 함께 커밋
  afterCommit → UserOutboxDispatcher
    B(REQUIRES_NEW): Outbox 잠금 → 이전 미발행 이벤트 확인 → Kafka 발행
      성공: PUBLISHED와 published_at 저장
      실패: PENDING 유지, 시도 횟수·오류 유형·다음 시도 시각 저장

스케줄러 → 발행 가능한 PENDING ID 조회 → 같은 발행 로직으로 재시도
```

회원 변경 작업을 비관적 잠금 아래 직렬화하고 회원 PK를 공유하는 UserKafkaEventState의 eventVersion을 증가시킨다. 회원 변경을 먼저 flush하여 같은 회원의 Outbox ID가 DB 변경 순서를 따르도록 한다. flush는 커밋이 아니므로 Outbox 저장 실패나 A의 커밋 실패 시 회원 변경도 롤백되고 Kafka는 호출되지 않는다. A가 커밋된 후 발행 콜백이 실행되지 않거나 B가 실패해도 PENDING 기록은 남는다. 콜백의 실패를 이미 커밋된 회원 변경의 실패 응답으로 바꾸지 않는다. 차단 정보 변경도 회원 DB와 Outbox 저장을 성공 기준으로 삼으며, AUTH 적용까지는 지연될 수 있다.

Outbox는 최초 topic·userId·멱등키·이벤트 버전·JSON payload를 보존한다. 이벤트에는 인증 정보 전체 상태와 마지막 비밀번호 재설정 버전을 담는다. 비밀번호는 기존 USER에서 암호화한 값만 기록한다. 회원 삭제 이후에도 이벤트를 발행할 수 있도록 회원 테이블에 대한 외래 키는 두지 않는다. 이벤트 payload나 원본 예외 메시지는 로그·실패 사유에 출력하지 않는다.

즉시 발행과 여러 서버의 스케줄러는 Outbox 행에 비관적 쓰기 잠금을 획득하고 상태를 다시 확인한다. 앞선 같은 userId의 PENDING 이벤트가 있으면 새 이벤트 발행을 미룬다. 다른 회원의 발행은 계속할 수 있다. 스케줄러는 회원별 가장 오래된 미발행 이벤트부터 가져오며, 뒤 이벤트는 후속 주기에 처리한다.

Kafka 성공 응답 뒤 B의 완료 상태 저장이 실패하면 같은 이벤트가 재발행될 수 있다. 이 구조는 최소 한 번 전달 방식이다. Outbox 저장 시 `idempotencyKey`를 한 번 생성하고 payload에 보존한다. 재발행과 DLT 원문 재처리도 같은 키를 유지한다. 새 작업은 새 키를 사용하며, 키나 양수 버전이 없는 Outbox는 발행하지 않고 오류를 기록한다. AUTH의 CREATE·UPDATE·DELETE 멱등 처리, 버전 검증 및 Redis 복구는 [회원 이벤트 소비 멱등성](./user-event-idempotency.md)을 따른다. 소비자 재시도·DLT·관리자 재처리 정책은 유지한다. DLT 재처리 시에는 기존 운영 절차대로 현재 회원 상태와 이벤트 순서를 확인한다.

## 설정

| 설정 | 기본값 | 역할 |
| --- | --- | --- |
| `app.user-outbox.scheduler-enabled` | `true` | 스케줄러 실행 여부. 커밋 직후 발행은 유지 |
| `app.user-outbox.fixed-delay-ms` | `5000` | 재시도 조회 주기 |
| `app.user-outbox.retry-delay-ms` | `30000` | 발행 실패 후 대기 시간 |
| `app.user-outbox.send-timeout-ms` | `10000` | Kafka 전송 Future의 확인 응답 대기 시간 |
| `app.user-outbox.batch-size` | `100` | 한 주기에서 조회하는 이벤트 수 |

Outbox 발행 재시도는 3회로 제한하지 않는다. 발행에 성공할 때까지 PENDING을 유지한다. AUTH가 이미 수신한 메시지의 업무 처리 실패에 적용되는 소비자 재시도·DLT와는 별개다. 운영 중에는 오래된 PENDING, attempt_count 증가와 last_error를 확인한다. PUBLISHED 정리는 별도 보관 정책에 따르며 PENDING은 삭제하지 않는다.

## DB 적용과 검증

`user-service/src/main/resources/sql/schema.sql`의 `user_outbox_events` 생성 구문과 인덱스를 추가했다. 현재 개발·운영 설정의 `ddl-auto: update`는 새 테이블을 생성한다. DDL을 수동 관리하는 환경에서는 이 테이블의 CREATE 및 인덱스 구문만 적용하며, 기존 DB에 초기화용 schema.sql 전체를 실행하지 않는다. 실제 DB 변경은 이 작업에서 실행하지 않았다.

`UserOutboxIntegrationTest`는 실제 H2 트랜잭션과 가짜 Kafka 응답으로 커밋 후 발행, 롤백·커밋 실패 시 미발행, Outbox 저장 실패 시 회원 롤백, Kafka 실패 후 같은 payload 재시도, 콜백 중단 복구, Kafka 성공 후 B 커밋 실패 재전달, 회원별 순서와 발행 작업 간 잠금을 검증한다. 실제 Kafka·PostgreSQL 장애 검증은 별도다.
