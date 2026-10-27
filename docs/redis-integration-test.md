# 좌석 Redis 통합 검증

## 실행

실행 중인 로컬 Redis를 사용한다. 기본 접속은 `127.0.0.1:6390`, 테스트 DB는 `15`다.
Redis가 실행되어 있어도 아래 환경 변수를 지정하지 않으면 이 테스트는 건너뛴다.

```powershell
$env:TICKET_REDIS_INTEGRATION = 'true'
.\gradlew.bat :ticket-service:test --tests dev.bum.ticket_service.service.seat.SeatRedisIntegrationTest
```

접속 위치가 다르면 `TICKET_TEST_REDIS_HOST`, `TICKET_TEST_REDIS_PORT`, `TICKET_TEST_REDIS_DATABASE`를 설정한다.
임시 ACL 사용자 생성·삭제를 위해 접속하는 기본 Redis 사용자에게 ACL 관리 권한이 필요하다.

## 검증 범위

- 실제 좌석 선점 서비스에 16개 동시 요청을 보내 정확히 한 요청만 성공하고 승자의 좌석 값과 잠금이 유지되는지 확인한다.
- 판매 시작 후 예열 Lua가 DB AVAILABLE 조건과 기존 Redis 상태·잠금을 보호하는지 확인한다.
- 재처리 Lua가 새 사용자 잠금을 변경하지 않고 이력을 PENDING으로 유지하는지 확인한다.
- 과거 RESERVED 이력을 현재 DB AVAILABLE 상태로 보정하고 공연 종료 기준 TTL을 설정하는지 확인한다.
- 테스트 전용 ACL 사용자에게 1·4번 좌석만 허용하여 실제 Redis 권한 오류를 발생시킨다. 2·3번 Outbox만 PENDING으로 남고 4번까지 동기화가 계속되는지 확인한다.
- Outbox Lua가 같은 예매의 잠금만 해제하고, 새 예매의 선점 값 또는 별도 잠금 키가 남아 있으면 변경하지 않는지 확인한다. 구매 매수는 DB 잠금과 구매 카운트로 검증한다.

Redis 명령과 Lua는 실제 서버에서 실행한다. 좌석 조회와 Outbox·기존 실패 이력 저장소는 테스트 대역으로 구성한다. 좌석 변경과 Outbox의 실제 DB 커밋·롤백, 서버 재시작에 해당하는 최초 전달 누락, 동시 작업자의 행 잠금은 별도 SeatCacheSyncOutboxIntegrationTest에서 검증한다. 자세한 실행 방법은 [좌석 Outbox](seat-cache-outbox.md)를 참고한다.

## 데이터 정리

테스트마다 임의 공연 ID와 임시 ACL 사용자 이름을 생성한다. 테스트에서 만든 키 및 ACL 사용자만 종료 시 삭제한다. `FLUSHDB`, `FLUSHALL`, 공유 컨테이너 중단은 실행하지 않는다.
프로세스를 강제로 종료한 경우 생성한 키나 ACL 사용자가 남을 수 있다. 키에는 TTL을 설정하며, 테스트의 임시 ACL 사용자 이름은 `ticket-integration-`으로 시작한다.
