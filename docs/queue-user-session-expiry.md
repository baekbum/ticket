# 대기열 사용자 세션 인덱스 만료

사용자별 `queue:user-sessions:{userId}` ZSET은 `waiting:{token}`과 `active:{token}`을 멤버로 저장한다.
score는 해당 토큰의 만료 시각(Unix epoch milliseconds)이며, 키 전체는 가장 늦은 score 시각에 만료된다.

## 상태 변경

- 대기 등록·갱신: Redis 서버 시각으로 토큰의 만료 시각을 정하고 토큰·이벤트 만료 인덱스·사용자 인덱스에 같은 값을 적용한다.
- WAITING 상태 조회: 실제 대기 토큰을 갱신한 후 사용자 인덱스의 최대 만료 시각을 다시 설정한다.
- READY 승격: waiting 멤버를 active 멤버로 교체한 후 만료 시각을 다시 계산한다.
- READY 재조회: 기존 토큰·멤버의 만료 시각을 연장하지 않는다.
- 완료·탈퇴·관리자 삭제: 멤버를 제거하고 남은 멤버의 최대 만료 시각을 적용한다. 마지막 멤버를 지우면 Redis가 빈 ZSET 키를 삭제한다.

`queue-session-expiry.lua`의 공통 함수로 `ZREVRANGE ... WITHSCORES`의 최대 score를 읽고 `PEXPIREAT`을 적용한다.
토큰·멤버 변경과 인덱스 TTL 변경은 같은 Lua 안에서 실행하므로 서로 다른 요청이 TTL을 덮어쓰는 사이에 끼어들지 않는다.
짧은 세션을 추가해도 다른 공연의 긴 세션이 먼저 만료되지 않는다.
유효하지 않은 요청은 토큰이나 사용자 인덱스의 만료 시각을 연장하지 않는다.

## 만료 멤버와 기존 데이터

키 전체의 TTL은 멤버별 만료를 대신하지 않는다. 재접속 시 기존 `prune` 경로에서 만료된 멤버를 정리한다.
멤버 목록을 조회한 뒤 실제 토큰 확인·멤버 제거·TTL 변경은 Lua로 함께 실행한다.
토큰의 `PEXPIRETIME`으로 살아 있는 멤버의 score도 보정하며, 조회 이후 삭제된 멤버는 다시 만들지 않는다.

기존 정리 스케줄러는 이벤트 인덱스를 정리한 뒤 `SCAN`으로 사용자 인덱스도 확인한다.
TTL이 없는 키(`-1`)에만 `prune`을 실행하여 이전 버전에서 남은 인덱스를 재방문 없이 정리하거나 TTL을 부여한다.
정상적으로 TTL이 설정된 키는 Redis의 자동 만료에 맡긴다.
SCAN의 COUNT는 기존 `app.queue.cleanup-scan-count`를 사용하고 작업은 순서대로 처리한다.

`PEXPIRETIME`을 사용하는 정리 경로는 Redis 7 이상을 기준으로 한다. 로컬 구성은 Redis 7.4를 사용한다.

## 검증

`QUEUE_TEST_REDIS_PORT`를 분리된 로컬 Redis 포트로 지정해 실행한다.

```powershell
.\gradlew.bat :queue-service:test :queue-service:nonBlockingTest
```

`ReactiveQueueUserSessionExpiryTest`는 토큰과 인덱스의 만료 시각 일치, 재방문 없는 자연 만료,
WAITING 갱신, READY 재조회, 여러 공연의 세션, 삭제 후 TTL 재계산, 동시 등록, 기존 인덱스 보정을 검사한다.
기존 대기열 생명주기·멱등성·동시성·HTTP 및 BlockHound 검사도 함께 실행한다.
Kafka 통합 테스트는 별도의 `EVENT_POLICY_TEST_KAFKA` 환경을 지정한 경우 실행한다.
