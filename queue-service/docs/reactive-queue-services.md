# WebFlux 전환 3단계: Reactive 대기열 서비스

> 이 문서는 전환 당시의 기록이다. 현재 구현과 배포 준비 상태는 [6단계 정리](webflux-finalization.md)를 참고한다.

> 이 문서는 해당 단계 당시의 기록이다. 현재 실행 경로는 [4단계 실행 앱 전환](webflux-runtime.md)을 참고한다.

## 변경 내용과 실행 경로

기존 MVC 컨트롤러는 기존 동기 서비스를 계속 사용한다. 같은 queue-service 앱 안에 아래 Reactive 서비스를 추가해 실제 Redis에서 동작을 검증했다.

| 기존 서비스 | 새 구현 |
|---|---|
| UserQueueSessionService | ReactiveUserQueueSessionService |
| ActiveQueueService | ReactiveActiveQueueService |
| WaitingQueueService | ReactiveWaitingQueueService |
| QueueService | ReactiveQueueService |
| QueueAdminService | ReactiveQueueAdminService |
| QueueRedisInspectService | ReactiveQueueRedisInspectService |
| 테스트 관리 컨트롤러의 직접 Redis 삭제 | ReactiveQueueTestManagementService |

4단계에서 컨트롤러와 인증/보안을 새 구현에 연결한다. 기존 구현 제거는 전환 검증 이후에 진행한다.

## 처리 방식

- Redis I/O는 ReactiveStringRedisTemplate으로 수행하며 서비스 내부에서 block(), 직접 subscribe(), 동기 Redis 호출을 사용하지 않는다.
- 등록 토큰과 만료 시각은 Mono.defer 내부에서 생성한다. 구독 전에는 등록하지 않고 같은 등록 Mono를 다시 구독하면 별도 요청처럼 처리한다.
- 승인 거절이나 조회 결과 부재는 빈 Mono로 표현한다. 유효성 검증은 항상 true/false를 반환한다.
- 순서를 지켜야 하는 삭제·갱신은 then으로 연결한다. 목록·세션 정리는 concatMap으로 순차 처리하며 관리자 bulk 응답의 사용자 순서도 유지한다.
- status는 정리 → 검증 → 입장 시도 → 승인 시 READY, 거절 시 갱신 → WAITING을 수행한다.
- 부수 효과가 있는 대체 경로는 switchIfEmpty(Mono.defer(...))로 구성한다.
- Redis 오류는 구독 시점에 기록하고 그대로 전파한다. 자동 재시도를 추가하지 않았다.
- 예상 입장 시간의 순번 반복 계산은 Schedulers.parallel()에서 실행한다. 기존 계산식을 유지했으므로 큰 순번에 비례하는 CPU 비용은 남아 있다.
- 없는 GET 결과를 관리자 응답에서 누락시키지 않고 value=null과 Redis TTL(-2/-1)을 보존한다.

## 공통 입장 승인 Lua

기존 ActiveQueueService의 Lua를 redis/queue-admit.lua 리소스로 옮겼다.
QueueRedisScripts.ADMIT_IF_SLOT_AVAILABLE를 동기·Reactive 구현이 함께 사용한다.

입장 정원/순번 확인, active 토큰 및 사용자 세션 생성, waiting 토큰과 인덱스 제거는 같은 Lua 실행 안에서 원자적으로 처리한다.
이번 작업에서 Lua 규칙·Redis 키·토큰 값 형식·기존 TTL 설정을 변경하지 않았다.

## 정리 스케줄러 선택

app.queue.cleanup-mode 설정으로 동기와 Reactive 정리가 동시에 실행되지 않도록 한다.

| 값 | 동작 |
|---|---|
| 생략 또는 blocking | 기존 QueueCleanupScheduler |
| reactive | ReactiveQueueCleanupScheduler |
| disabled | 테스트에서 주기 작업 비활성화 |

기본 실행은 기존 동기 정리를 유지한다. 새 스케줄러는 Mono<Void>를 반환하고 Spring이 매 주기 구독한다.
Mono.defer로 각 실행의 상태를 새로 생성하며 fixedDelay는 정리가 완료된 뒤부터 계산된다.
직접 subscribe()로 작업을 분리하거나 scheduler에서 block()으로 기다리지 않는다.
한 주기 실패 후 다음 주기를 계속 수행하는 것은 Spring 스케줄러의 동작이며 서비스에서 실패를 삼키는 처리는 없다.

## 검증

전용 테스트 Redis를 사용한다. 1·2단계 HTTP 테스트는 DB 2, 신규 Reactive 통합 테스트는 DB 3을 사용해 백그라운드 정리 간섭을 줄인다.
QUEUE_TEST_REDIS_PORT가 없으면 실제 Redis 통합 테스트는 건너뛴다.

~~~powershell
docker run --detach --rm --name ticket-queue-baseline-redis --publish 127.0.0.1:16390:6379 redis:7.4-alpine redis-server --appendonly no
$env:QUEUE_TEST_REDIS_PORT = '16390'
.\gradlew.bat :queue-service:test --no-daemon --console=plain
docker stop ticket-queue-baseline-redis
~~~

신규 실제 Redis 테스트 15개:
- 등록의 지연 실행과 재구독 시 서로 다른 토큰 발급
- MVC 발급 waiting 토큰의 Reactive 승격과 그 반대 방향
- 완료·재완료·검증·승격 후 waiting 토큰 재조회
- 입장 정원 도달 시 WAITING 응답 및 TTL/인덱스 갱신
- 세션 제한, 강제 waiting/active 세션 교체
- waiting/active 이탈과 다른 사용자 토큰 거부
- 사용자·토큰 검증 오류의 구독 시점 전달
- MGET의 누락 항목 정리 및 빈 세션 처리
- SCAN 기반 이벤트 만료 정리 및 잘못된 이벤트 ID 건너뛰기
- 관리자 bulk 응답 순서·기존 active 토큰 유지
- 관리자 조회의 누락 값/음수 TTL/limit 및 삭제 결과
- 관리자 active 삭제 시 사용자 세션 정리
- 테스트용 이벤트 삭제 범위 호환
- 실제 Lua의 12개 동시 승격 요청에서 정원 2 유지

별도 테스트:
- Redis 오류가 원래 예외로 전달되고 승격/갱신으로 복구하지 않는지 확인
- Reactive 스케줄러만 선택되는지, 정리가 겹치지 않는지, 실패 후 다음 주기에도 실행되는지 확인

2026-10-09 검증 결과: queue 테스트 **43개 모두 통과**, 실패·오류·건너뜀 0개.
기존 26개에 실제 Redis 통합 15개, 오류 전파 1개, 스케줄러 1개를 추가했다.
compileJava 및 test 빌드 성공. 검증 후 전용 Redis 컨테이너는 종료했다.

## 유지한 기존 제약

- 승격된 waiting 토큰의 재조회는 오류이며 멱등하지 않다.
- 사용자 세션의 이벤트 간 강제 제거, waiting 만료 후 사용자 인덱스 정리 제약을 그대로 유지한다.
- 만료 정리·일반 삭제·세션 제한은 여러 명령으로 수행한다. 입장 승인 Lua 외 작업까지 원자적이라고 가정하지 않는다.
- 테스트용 이벤트 삭제는 기존처럼 waiting/active ZSET과 active 토큰 등을 삭제하며 waiting 만료 인덱스·사용자 세션 전체 정리를 보장하지 않는다.
- 관리자 조회의 명령 수와 ETA 계산 비용은 이번 단계의 최적화 범위가 아니다.
- HTTP 요청 취소, 응답 유실, Redis 장애/복구 및 여러 앱 인스턴스의 정합성은 5단계 검증 대상이다.
- 아직 WebFlux 서버의 처리량이나 이벤트 루프 블로킹을 부하/BlockHound로 검증한 상태는 아니다.
