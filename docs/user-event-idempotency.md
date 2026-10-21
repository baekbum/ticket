# AUTH 회원 이벤트 멱등성과 버전 검증

회원 이벤트는 `id`(회원 PK), `idempotencyKey`(작업 식별자), `eventVersion`(회원별 변경 순서)을 함께 전달한다. `eventVersion`은 Redis 세션 폐기에 사용하는 `tokenVersion`과 별개다.

## USER 버전 발급과 전체 상태

회원 생성은 버전 1부터 시작한다. 인증 정보 변경·탈퇴·관리자 삭제 이벤트를 만들 때마다 `UserKafkaEventState.eventVersion`을 증가시킨다. `User`에는 이벤트 버전 필드를 두지 않는다. 상태 엔티티는 `@OneToOne + @MapsId`로 회원 PK를 공유하며 USER DB의 `user_kafka_event_states.user_pk`가 PK이자 `users.id`의 FK다. 역방향 연관관계는 두지 않는다. 변경 작업은 회원을 비관적 잠금 조회하고 최신 DB 상태로 갱신한 뒤 진행한다. 회원 변경, 상태 엔티티의 버전 증가, Outbox 저장은 같은 트랜잭션으로 커밋·롤백한다. 회원 생성 시 첫 이벤트와 함께 상태를 생성한다. 삭제 시에는 상태의 마지막 버전을 Outbox에 기록한 뒤 상태와 회원을 삭제한다. 회원 조회나 AUTH와 관계없는 정보 변경은 이벤트 버전을 증가시키지 않는다.

UPDATE도 암호화된 비밀번호·권한·계정 상태·차단 정보 전체를 담는다. 예를 들어 비밀번호 변경 v2가 DLT에 있고 권한 변경 v3가 먼저 반영돼도 v3 스냅샷에 새 비밀번호가 있으므로, v2를 건너뛰어도 해당 변경이 유실되지 않는다.

`passwordResetVersion`은 0에서 시작하며 비밀번호 찾기를 통한 재설정마다 기존 값에서 1씩 증가하는 별도 버전이다. 일반 정보 변경 시에는 증가시키지 않고 이후 스냅샷에도 이 값을 유지한다. AUTH는 자신이 반영한 재설정 버전보다 클 때만 로그인 잠금을 해제한다. 따라서 재설정 이벤트를 건너뛰어도 최신 스냅샷으로 잠금 해제를 이어받으며, 그 뒤 새로 발생한 잠금은 같은 재설정 버전으로 해제하지 않는다.

Outbox에 키·버전·전체 payload를 저장한 후 재발행 시 그대로 사용한다. 소비나 재발행 시 새 키·버전을 생성하지 않는다. 키 또는 양수 버전이 없는 이벤트는 거부한다.

## AUTH 등록·검증과 직렬화

`UserEventIdempotencyService.registerAndValidate()`는 MANDATORY로 회원 변경 트랜잭션에 참여한다.

1. `(auth_id, idempotency_key)` UNIQUE 제약과 INSERT ON CONFLICT DO NOTHING으로 처리 이력을 등록한다.
2. 처리 이력을 비관적 잠금 조회하고 요청 SHA-256을 비교한다. 해시는 회원 PK·이벤트 버전·재설정 버전·이벤트 종류와 요청 내용을 포함한다. 같은 키로 내용을 바꾸면 거부한다.
3. `user_event_versions`에 회원별 행을 만들고 비관적 잠금 조회한다. 서로 다른 키의 동시 이벤트도 이 행 아래 직렬화된다.
4. 현재 버전의 같은 요청이면 `DUPLICATE`, 이미 반영한 버전 이하이거나 삭제된 계정이면 `STALE`, 새 버전이면 `REGISTERED`를 반환한다.

회원 반영 실패나 DB 커밋 실패 시 처리 이력과 마지막 버전도 함께 롤백한다. 높은 버전이 먼저 잠금을 획득하면 뒤의 낮은 버전은 건너뛴다. 낮은 버전이 먼저 처리되면 그 뒤 높은 버전을 적용하여 최종 상태는 최신 버전이 된다.

과거 이벤트 자체는 처리 오류가 아니므로 정상적으로 소비를 끝낸다. 따라서 오래된 DLT 이벤트를 재처리해도 최신 상태를 덮어쓰거나 같은 이벤트가 반복해서 DLT로 이동하지 않는다. 별도의 Redis 장애가 발생하면 인증 상태 게시를 위해 기존 소비자 재시도 경로를 사용한다.

## 삭제와 Redis 복구

CREATE·UPDATE·관리자 DELETE 모두 처리 이력과 버전 검증을 적용한다. 일반 회원 탈퇴는 UPDATE/WITHDRAWN이다. DELETE가 완료돼도 회원별 버전 행과 삭제 여부를 남겨 과거 CREATE가 계정을 되살리지 못하게 한다. 처리 이력·버전 행에는 회원 외래 키를 두지 않는다.

중복·과거 CREATE/UPDATE는 DB 변경을 생략하고 현재 AUTH DB 상태를 Redis에 게시한다. 회원 PK가 다르거나 이미 삭제된 계정이면 게시하지 않는다. 중복 DELETE는 DB 삭제 뒤 실패했던 Redis 폐기를 다시 진행하지만, 과거 DELETE는 현재 계정을 삭제하지 않는다.

AUTH DB 커밋과 Redis 게시는 별개다. DB 커밋 뒤 Redis 게시가 실패하면 Kafka 재시도가 DB 변경을 반복하지 않고 현재 인증 상태를 복구한다.

## DB 적용

실제 DB DDL은 실행하지 않는다. 초기화용 `auth-service/src/main/resources/sql/schema.sql`, `user-service/src/main/resources/sql/schema.sql`에 새 구조를 반영했다. 기존 DB에 초기화 SQL 전체를 실행하면 데이터가 삭제되므로 스키마 변경만 적용해야 한다.

필요한 구조는 다음과 같다.

- USER 신규 `user_kafka_event_states`: 회원 PK를 공유하는 1:1 상태 테이블. `users`에 버전 컬럼을 추가하지 않는다.
- AUTH `auth`: `password_reset_version BIGINT NOT NULL DEFAULT 0`
- AUTH `processed_user_events`: `event_version BIGINT NOT NULL`, 이벤트 종류에 DELETE 허용
- AUTH 신규 테이블:

```sql
CREATE TABLE user_event_versions (
    auth_id BIGINT PRIMARY KEY,
    last_event_version BIGINT NOT NULL DEFAULT 0 CHECK (last_event_version >= 0),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);
```

```sql
CREATE TABLE user_kafka_event_states (
    user_pk BIGINT PRIMARY KEY REFERENCES users(id),
    event_version BIGINT NOT NULL DEFAULT 0 CHECK (event_version >= 0),
    password_reset_version BIGINT NOT NULL DEFAULT 0 CHECK (password_reset_version >= 0),
    CONSTRAINT chk_user_kafka_reset_version CHECK (password_reset_version <= event_version)
);
```
AUTH의 `ddl-auto: validate` 설정에서는 배포 전에 스키마 변경이 필요하다. USER와 AUTH가 같은 이벤트 계약을 사용해야 한다. 이미 발행된 버전 없는 이벤트나 기존 처리 이력의 버전은 자동으로 추측하지 않는다. 재처리할 때 키·버전·내용을 보존하며, 보정 작업은 USER에서 새 버전의 이벤트로 생성한다. 회원 PK와 원본 버전·Outbox 시퀀스를 임의로 초기화하면 기존 순서 보장이 깨지므로 DB 재구축 범위를 함께 관리한다.

## 검증

H2 PostgreSQL 모드에서 중복과 요청 불일치, 미처리 과거 DLT 이벤트, 동일 버전의 다른 키, 삭제 뒤 CREATE와 과거 DELETE 재전달, 비밀번호 재설정 누락 복구, 서로 다른 키의 동시 처리, DB 실패·롤백, Redis 실패 복구를 검증한다. USER는 회원과 상태의 PK 공유, 버전 증가·전체 스냅샷·동시 변경·롤백·재발행 보존, 삭제 이벤트 기록 후 상태 정리를 검증한다. 실제 Redis 테스트는 환경변수를 활성화해 별도로 실행한다. 실제 PostgreSQL·Kafka 장애를 결합한 종단 검증은 별도다.
