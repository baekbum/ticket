# 좌석 상태의 Redis 동기화 Outbox

결제 완료, 예매 취소·환불·만료, 관리자 상태 보정에서 좌석 DB 상태를 변경할 때 `seat_cache_sync_outbox`에도 좌석별 작업을 같은 트랜잭션으로 저장한다. `SeatCacheSyncOutboxService.enqueue`는 `MANDATORY`이므로 원래 트랜잭션이 없으면 거부한다. Outbox 저장이 실패하면 좌석 변경도 롤백하며, 원래 트랜잭션이 롤백되면 Redis를 호출하지 않는다.

커밋 후 콜백은 저장된 Outbox ID만 전달한다. 별도 `REQUIRES_NEW` 작업자가 Outbox 행을 잠근 뒤 최신 DB 좌석을 비관적 락으로 조회하고 Redis 반영까지 좌석 락을 유지한다. 최초 콜백을 놓치거나 서버가 종료되어도 기본 30초 스케줄러가 `PENDING` 작업을 최대 100건씩 재처리한다. 서로 다른 작업자가 같은 ID를 처리해도 완료 상태와 행 잠금으로 중복 실행을 제어한다.

작업에는 과거 목표 상태를 고정하지 않는다. 오래된 완료 작업을 취소 작업보다 늦게 처리해도 현재 DB 상태를 적용한다. Redis 키는 최초 좌표와 일치하는지 확인하며, 삭제되거나 좌표가 바뀐 좌석은 임의로 덮어쓰지 않고 오류 유형을 남겨 `PENDING`으로 보존한다.

예매의 사용자·주문으로 만든 잠금 값도 함께 저장한다. Lua 스크립트는 좌석 값과 별도 잠금 키를 확인하고, 다른 예매의 선점이 있으면 갱신하거나 해제하지 않는다. 같은 예매가 가진 선점만 상태 반영과 함께 해제한다. DB에는 상태를 기록하되 Redis 적용이 지연될 수 있으므로 최종 예매의 DB 검증·잠금은 계속 유지한다.

Redis 오류·응답 유실·다른 사용자 선점은 `PENDING`, 실패 횟수, 다음 시도 시각, 오류 유형으로 보존한다. Redis 반영 후 Outbox 완료 커밋에 실패해도 `PENDING`이 남아 안전하게 다시 적용한다. 완료 시 상태는 `APPLIED`가 된다. 커밋 이후 실패는 이미 확정한 원래 업무 요청을 실패 응답으로 바꾸지 않는다.

기존 `seat_cache_sync_failures`의 조회·수동 재처리 기능은 유지한다. 새 동기화의 진행·실패 상태는 Outbox에서 관리하며, 매 시도마다 별도 실패 이력을 중복 생성하지 않는다. 운영 시 `PENDING` 건수·가장 오래된 생성 시각·재시도 횟수·오류 유형을 확인한다.

## 설정

- `app.seat-cache.sync-outbox.fixed-delay-ms`: 기본 30000
- `app.seat-cache.sync-outbox.retry-delay-ms`: 기본 30000
- `app.seat-cache.sync-outbox.batch-size`: 기본 100

테이블·인덱스 정의는 [전체 스키마](../ticket-service/src/main/resources/sql/schema.sql)에 포함되어 있다. 기존 DB에는 배포 전에 해당 파일의 `seat_cache_sync_outbox` 생성문과 인덱스 생성문을 적용한다. 기존 실패 이력의 수동 복구 방식은 유지되며, 배포 이전에 작업 자체가 유실된 건은 새 Outbox만으로 소급 복원되지 않는다.

## 검증

`SeatCacheSyncOutboxIntegrationTest`는 기본 H2로 실행하고, `TICKET_SEAT_OUTBOX_POSTGRES_PORT`를 지정하면 검증 전용 PostgreSQL `seat_outbox_test`(계정·비밀번호 `seat_test`)를 사용한다. 테스트는 테이블을 생성·삭제하므로 임시 컨테이너에만 연결한다. 커밋·롤백·기록 실패, 최초 실행 누락, Redis 장애와 재시도, 작업 순서 역전, 완료 상태 커밋 실패, 여러 작업자와 좌석 변경의 동시성을 검증한다.

`SeatRedisIntegrationTest`의 실제 Redis 검증은 `TICKET_REDIS_INTEGRATION=true`로 활성화한다. 좌석·잠금 키의 소유자 보호와 ACL 오류 시 좌석별 작업 보존을 확인하며, 테스트가 만든 키·ACL 사용자만 정리한다.
