# 대기 토큰을 사용한 승격 결과 복구

## HTTP 계약

같은 사용자·이벤트의 같은 대기 토큰으로 status를 다시 호출하면, 활성 세션이 유효한 동안 기존 active 토큰과 최초 만료 시각을 반환한다.
외부 요청 경로와 응답 DTO는 유지한다.

| 상태 | 응답 |
|---|---|
| 아직 입장할 수 없는 유효한 대기 토큰 | 200 / WAITING, 대기 TTL 갱신 |
| 최초 승격 | 200 / READY, active 토큰 발급 |
| 승격 후 같은 대기 토큰으로 재조회 | 200 / READY, 기존 active 토큰 반환 |
| 다른 사용자·이벤트의 대기 토큰 | 400 / QUEUE_TOKEN_INVALID |
| 완료·탈퇴·관리자 삭제·강제 교체·만료된 활성 세션 | 400 / QUEUE_TOKEN_INVALID |
| 매핑의 active 토큰·활성 인덱스·역매핑이 유효하지 않음 | 400 / QUEUE_TOKEN_INVALID, 새 토큰 발급 없음 |

재조회는 토큰 유효 시간을 연장하지 않는다.
토큰과 activeTokenExpiresAt은 동일하지만, 남은 초·대기 인원·조회 시점에 따라 달라지는 응답 필드는 현재 값으로 계산한다.
완료 후 같은 대기 토큰을 재사용해 새로운 슬롯을 받는 계약은 아니다. 새로 등록해 새 대기 토큰을 받아야 한다.

## Redis 데이터

기존 active 토큰의 값(eventId:userId), 대기열·활성 ZSET 및 사용자 세션 형식은 유지한다.

| 키 | 값 |
|---|---|
| queue:waiting-active:{waitingToken} | activeToken |
| queue:active-waiting:{activeToken} | waitingToken |
| queue:active-token:{activeToken} | 기존 eventId:userId |

토큰·정방향 매핑·역매핑을 같은 Lua에서 SET PXAT으로 저장한다.
Redis TIME을 기준으로 계산한 같은 절대 만료 시각을 세 키와 활성·사용자 세션 ZSET score에 적용한다.
단순히 각각 같은 길이의 TTL을 설정하는 방식과 달리 최초 만료 시각 자체가 일치한다.

신규 Lua로 승격한 토큰부터 복구 매핑이 생긴다.
기존 활성 토큰의 검증·완료는 계속 가능하지만, 이미 삭제된 대기 토큰의 과거 관계를 소급 복원하지 않는다.
비교용 동기 구현의 기존 승격 Lua는 그대로 유지하며 실제 HTTP·관리자 Reactive 경로는 새 승격 Lua를 사용한다.

## 승격 판단

ReactiveQueueService.status는 대기 토큰이 삭제됐다는 이유로 먼저 거부하지 않는다.
만료 정리 후 ReactiveActiveQueueService.admit가 새 queue-status.lua의 판단 결과를 처리한다.

1. Java에서 정방향 매핑의 active 토큰을 조회한다. 이 값은 기존 토큰 키를 KEYS에 명시하기 위한 힌트다.
2. Lua에서 현재 정방향 매핑을 다시 확인한다. 조회 사이에 승격해 힌트와 값이 다르면 쓰기 없이 RETRY를 반환한다.
3. Java가 매핑을 다시 읽고 Lua에서 재검증한다. 최초 실행을 포함해 최대 3회로 제한하며 계속 불일치하면 503을 반환한다.
4. 기존 매핑이 있으면 active 토큰 소유자(eventId:userId), 역매핑의 원래 대기 토큰, 활성 ZSET 멤버와 만료 시각, 토큰 TTL을 확인하고 기존 active 토큰을 반환한다.
5. 매핑이 없으면 대기 토큰 소유자·대기 순번·입장 슬롯을 확인한다.
6. 대기 상태이면 같은 Lua에서 대기 TTL과 인덱스를 갱신한다. 입장 가능하면 active 토큰·두 매핑을 저장하고 대기 토큰·인덱스를 제거한다.

조회한 힌트를 그대로 신뢰하지 않으며, 스크립트가 사용하는 모든 Redis 키를 KEYS로 전달한다.
Lua 내부에서 토큰 문자열로 미리 전달되지 않은 키를 조합하지 않는다.
admit의 빈 Mono는 대기 상태, 토큰 값은 최초 승격 또는 기존 승격 결과이며 유효하지 않은 상태는 예외로 전달한다.

## 삭제

완료·활성 토큰 탈퇴·관리자 삭제·강제 교체·만료 정리는 같은 활성 토큰 삭제 경로를 사용한다.
active 토큰으로 역매핑을 찾고 삭제 Lua에 두 매핑 키를 전달한다.
정방향 매핑이 삭제 대상 active 토큰을 가리키는지 확인한 뒤 토큰·인덱스·사용자 세션·두 매핑을 함께 삭제한다.
다른 active 토큰을 가리키는 매핑은 삭제하지 않는다.

이벤트 전체 삭제는 조회한 활성 토큰 목록을 Lua에서 다시 확인한다.
조회 후 승격·완료로 목록이 달라졌다면 아무 키도 지우지 않고 다시 조회한다.
목록이 같을 때 이벤트 키와 조회한 active 토큰·매핑을 원자적으로 삭제하여 새로 승격한 매핑을 놓치지 않는다.
기존 테스트용 삭제 API의 나머지 삭제 범위는 유지하며, 반환하는 실제 Redis 삭제 키 수에는 새 매핑 키도 포함한다.

삭제 코드 밖에서 Redis 키를 직접 손상한 경우에도 유효하지 않은 매핑으로 READY를 반환하거나 새 토큰을 발급하지 않는다.
응답 유실 복구는 승격이 성공한 상태를 다시 읽는 기능이다. Redis 데이터 유실이나 Lua 실행 오류를 자동으로 롤백하는 기능은 아니다.

## 2026-10-12 검증

- queue 일반 테스트 82개, nonBlockingTest 2개 통과. 실패·오류·건너뜀 0개.
- 신규 멱등성 테스트 19개: 순차·24개 동시 재조회, 힌트 조회 경쟁, 정확한 만료 시각·TTL 미연장, 다른 사용자·이벤트·다른 대기 토큰의 매핑 거부, 승격 후 구독 취소, 완료·탈퇴·관리자 삭제·강제 교체·만료·이벤트 삭제 및 손상된 매핑을 검증했다.
- 실제 HTTP 테스트에서 승격 후 재조회가 같은 active 토큰과 만료 시각을 반환하고, 완료 후 400으로 거부되는 것을 확인했다.
- 별도 BlockHound JVM에서 기존 요청 경로와 저장된 승격 결과 복구 경로도 통과했다. 블로킹 허용 예외는 추가하지 않았다.
- 실행 JAR 빌드 성공.

두 실제 JVM(18084/18085)과 전용 Redis(16401)를 사용한 검증:
- 승격 후 두 인스턴스로 보낸 재요청 24건 모두 같은 active 토큰·만료 시각 반환. 활성 슬롯 1개.
- 동일 사용자 동시 등록 24건은 등록 2건·한도 응답 22건 유지.
- 서로 다른 사용자 12명은 READY 2명·WAITING 10명 유지.
- Redis pause 중 인증 약 2.198초·대기열 validate 약 2.433초 후 503, unpause 후 등록 복구.
- 응답 유실로 취급한 첫 승격을 재조회하여 200 / 동일 active 토큰 복구.
- 완료 후 재조회는 400, 정·역방향 매핑 잔여 키 0개.

원본은 docs/baseline/results/idempotency/validation/{summary.json,validation.json,app-18084.log,app-18085.log}에 보존한다.
5단계 부하 비교는 이 변경 전 결과다. 이번 작업에서는 전체 부하 비교를 다시 실행하지 않았다.

~~~powershell
docker run --detach --rm --name ticket-queue-idempotency-redis --publish 127.0.0.1:16391:6379 redis:7.4-alpine
$env:QUEUE_TEST_REDIS_PORT = '16391'
.\gradlew.bat :queue-service:test :queue-service:nonBlockingTest :queue-service:bootJar --no-daemon --console=plain
docker stop ticket-queue-idempotency-redis

& queue-service/docs/baseline/validate-runtime.ps1 -Jar queue-service/build/libs/queue-service-0.0.1-SNAPSHOT.jar
~~~