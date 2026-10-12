# Queue WebFlux 전환 전 기준 (1단계)

## 범위와 실행 상태

운영 코드는 변경하지 않는다. API 계약, Redis 상태 전이, 인증 동작을 기록하고 같은 HTTP 테스트/부하 시나리오를 전환 후 재사용한다.

- 기준 소스: `aed456b935a819c32d8d3493d87ff14fec20ee71`
- Java 21 / Spring Boot 3.4.1 / MVC(Tomcat), 동기 StringRedisTemplate.
- 기존: Mockito 서비스 테스트 13개 + 앱 contextLoads 1개.
- 추가: 실제 HTTP + JWT 필터 + 별도 인증 Redis + 실제 대기열 Redis/Lua 통합 테스트 9개.
- 통합 테스트는 `QUEUE_TEST_REDIS_PORT`를 지정해야 실행된다. 미지정 시 **건너뛰며**, 통합 검증 성공으로 간주하지 않는다.
- 환경/성능 측정 결과는 아래 결과 기록 및 `results/`의 원본으로 구분한다.

## 보존할 API 계약

경로 접두사는 `/api/v1/queue`. 사용자 API는 Bearer access JWT와 인증 Redis 상태가 필요하다.

| 요청 | 현재 응답/효과 |
|---|---|
| POST /events/{eventId}/enter | 200 WAITING, 새 waiting token. 헤더 X-Queue-Force-Enter 기본 false |
| 세션 한도 도달 | 200 SESSION_LIMIT_CONFIRM_REQUIRED. force=true이면 가장 먼저 만료되는 세션 제거 후 등록 |
| GET /events/{eventId}/status + X-Waiting-Token | 200 WAITING 또는 READY. 조회 중 정리·갱신·승격도 수행 |
| 잘못된/다른 사용자의 waiting token | 400, code=QUEUE_TOKEN_INVALID |
| POST /events/{eventId}/leave | X-Waiting-Token 또는 X-Active-Token, 204. 반환값과 무관하게 204 |
| POST /validate | JWT 없이 허용. eventId/userId/token JSON. 200 allowed=true, reason=OK 또는 false/INVALID_QUEUE_TOKEN |
| POST /complete | JWT 없이 허용. 동일 JSON. 성공 204, 무효 토큰 400 |
| /validate·/complete 필수 필드 누락 | 400, code=VALIDATION_FAILED, details에 필드 오류 |
| 인증 없이 사용자 API 호출 | 현재 403 |
| refresh token으로 호출 / 폐기된 access token | 401. 인증 실패는 업무 오류와 다른 status/error JSON 형식 |
| 변조/형식이 잘못된 JWT | 현재 403 |
| USER의 관리 API 호출 | 403 |

응답 필드: eventId, status, rank, waitingCount, token, expiresInSeconds, estimatedEntryAt, activeTokenExpiresAt.
READY의 rank는 0, WAITING 순번은 1부터 시작한다. 날짜와 UUID의 구체적 값은 고정하지 않는다.

## Redis 및 처리 규칙

- queue DB 2 / 인증 상태 DB 0을 별도 연결로 사용.
- waiting ZSET: `queue:event:{eventId}:waiting`, 점수는 등록 시각(ms).
- 만료 인덱스: `queue:event:{eventId}:waiting-expiry`.
- active ZSET: `queue:event:{eventId}:active`, 점수는 만료 시각(ms).
- 토큰: `queue:waiting-token:{token}`, `queue:active-token:{token}`, 값 `eventId:userId`, TTL 적용.
- 사용자 세션: `queue:user-sessions:{userId}`, member는 waiting:{token}/active:{token}.
- 인증: `AUTH:STATE:{userId}`, 값 `version:activeFlag:role`.
- Lua가 입장 가능 수와 순번을 확인하고 active 생성 + waiting 제거를 원자적으로 수행.
- 스케줄러는 만료 정리용이다. 승격은 status 요청 및 관리자 bulk status에서 수행한다.
- 설정 기본(local): admission-size=5, max-sessions-per-user=4, active TTL=20m, waiting TTL=1m, cleanup=20s.
- 통합 테스트는 admission-size=2, max-sessions-per-user=2로 제한 경계를 재현한다.

## 현재 동작 중 주의할 사항

현재 동작의 기록이며 WebFlux 전환에서 자동으로 개선됐다고 가정하지 않는다.

- 승격된 waiting token을 다시 status에 보내면 400이다. 응답 유실 후 재시도가 멱등하지 않다. 테스트에 현재 동작을 명시했다.
- enter 자체도 멱등 등록이 아니다. 같은 사용자 요청마다 새 세션이 생긴다.
- 밀리초 점수가 같은 waiting 항목은 등록 요청 순서와 Redis 정렬 순서가 다를 수 있다. 엄격한 동시 FIFO를 이번 테스트가 보장하지 않는다.
- 사용자 세션은 이벤트 간 공유되지만 강제 제거는 요청 eventId를 사용한다. 다른 이벤트의 세션을 제거하는 경우는 별도 정합성 검토가 필요하다.
- waiting 만료 정리는 요청 userId 또는 null을 사용하므로, 다른 사용자의 세션 인덱스 정리까지 완전하다고 가정하지 않는다.
- 관리자 조회의 항목별 GET/TTL, 요청 경로의 만료 정리, 순번만큼 반복하는 ETA 계산은 별도 최적화 대상이다.

## 테스트 재현 (저장소 루트, PowerShell)

기존 서비스 Redis 대신 새 컨테이너만 사용한다. 아래 이름/포트가 사용 중이면 기존 컨테이너를 임의로 삭제하지 말고 충돌을 확인한다.

~~~powershell
docker run --detach --rm --name ticket-queue-baseline-redis --publish 127.0.0.1:16390:6379 redis:7.4-alpine redis-server --appendonly no
$env:QUEUE_TEST_REDIS_PORT = '16390'
.\gradlew.bat :queue-service:test --no-daemon --console=plain
~~~

통합 테스트는 고유 eventId/userId로 데이터를 생성한다. Redis 장애나 Lua 정합성을 mock 결과로 대체하지 않는다.
테스트 종료 후 컨테이너를 제거하면 생성 데이터도 폐기된다. 공유/운영 Redis에는 실행하지 않는다.

자동 검증 범위:
1. 등록 → 승격 → 검증 → 완료, Redis 키/세션 정리, 재완료 및 waiting token 재조회
2. waiting/active 이탈
3. 세션 제한 및 강제 진입
4. 없는/타 사용자 waiting token
5. 입력 검증
6. 미인증·refresh·폐기·잘못된 JWT
7. 실제 waiting 키 만료와 이벤트 인덱스 정리
8. USER의 관리 API 차단
9. 12개의 동시 status 요청에서 active 정원 2 및 나머지 WAITING 확인

미검증: 다중 앱 인스턴스, Redis 장애/복구, 네트워크 응답 유실, active 만료 경계의 경합, 이벤트 간 강제 제거, 관리자 API 전체 계약. 5단계에서 확대한다.

## 부하 시나리오 재현

별도 앱 포트 18083, 별도 Redis 16390. JWT 서명 키는 **테스트 전용 공개 문자열**이며 실제 환경 키를 사용하지 않는다.

~~~powershell
.\gradlew.bat :queue-service:bootJar --no-daemon --console=plain
java -Xms256m -Xmx512m -jar queue-service/build/libs/queue-service-0.0.1-SNAPSHOT.jar --server.port=18083 --spring.profiles.active=baseline --spring.data.redis.host=127.0.0.1 --spring.data.redis.port=16390 --spring.data.redis.database=2 --token-state.redis.host=127.0.0.1 --token-state.redis.port=16390 --token-state.redis.database=0 --token.secret=queue-baseline-only-secret-at-least-32-bytes-long --app.queue.admission-size=5 --management.tracing.enabled=false
~~~

다른 터미널에서:

~~~powershell
& queue-service/docs/baseline/prepare-users.ps1 -Users 100 -RunId mvc-baseline
New-Item -ItemType Directory -Force queue-service/docs/baseline/results/mvc-baseline | Out-Null
$scriptDir = (Resolve-Path queue-service/docs/baseline).Path
$resultDir = (Resolve-Path queue-service/docs/baseline/results/mvc-baseline).Path
docker run --rm --add-host=host.docker.internal:host-gateway -v "${scriptDir}:/scripts:ro" -v "${resultDir}:/results" -e RUN_ID=mvc-baseline -e EVENT_ID=900001 -e VUS=100 -e DURATION=60s grafana/k6:0.57.0 run /scripts/queue-load.js
~~~

`prepare-users.ps1`는 테스트 Redis의 인증 DB에만 2시간 TTL의 사용자 상태를 생성한다.
JWT는 k6 각 가상 사용자(VU)에서 발급하고 같은 실제 인증 필터를 통과한다. 사용자 수는 VUS 이상이어야 한다.

- lifecycle(기본): 등록 → 1초 간격 폴링 → READY 후 5초 머무름 → 완료 → 재등록.
- MODE=waiting: 입장한 5명은 슬롯을 유지하고 나머지는 계속 폴링한다. active TTL(20분)보다 짧게 실행한다.
- VUS=20/100/500, DURATION, POLL_SECONDS, HOLD_SECONDS, EVENT_ID, RUN_ID, BASE_URL을 명시해 조건을 기록한다.
- constant-vus는 응답 지연 + sleep에 따라 실제 요청률이 낮아지는 **닫힌 부하 모델**이다. 고정 RPS나 최대 처리량 측정으로 해석하지 않는다.
- http_req_failed < 1%, 업무 check 성공률 > 99%만 실행 유효성 기준으로 둔다. 응답 시간 SLO는 아직 정하지 않았다.
- 결과 JSON은 parameters, 전체 HTTP 및 status 전용 p95/p99, 입장/완료 수를 보존한다. 결과 디렉터리는 Git에서 제외한다.
- warm-up과 측정 구간을 구분하고, 정식 비교는 동일 머신/자원/JVM/Redis/토큰 수로 3회 반복한다.
- 각 독립 비교 전 전용 Redis를 새로 생성한다. warm-up을 별도 이벤트로 실행했다면 그 데이터도 측정 배경 부하가 되므로 동일하게 맞춘다.
- /actuator/prometheus에서 process_cpu_usage, jvm_memory_used_bytes, jvm_threads_live_threads, process_cpu_time_ns를 주기적으로 수집한다. Redis INFO stats/commandstats와 호스트 CPU도 함께 기록한다.
- 요청률, status p95/p99, 오류율, CPU, 힙, 스레드 수를 함께 비교한다. 앱과 부하 발생기가 같은 머신이면 자원 경쟁이 있으므로 운영 성능으로 일반화하지 않는다.
- 운영 Redis에는 FLUSHDB/FLUSHALL을 실행하지 않는다. 정리는 이 문서에서 생성한 앱 종료 및 아래 컨테이너 종료로 한정한다.

~~~powershell
docker stop ticket-queue-baseline-redis
~~~

참고: [k6 constant-vus 실행 모델](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/constant-vus/).

자원 샘플러는 k6 실행과 동시에 다른 터미널에서 실행한다 (약 2초 간격, 요청 소요 시간 포함).

~~~powershell
& queue-service/docs/baseline/sample-resources.ps1 -Seconds 75 -OutputDirectory queue-service/docs/baseline/results/mvc-baseline
~~~

샘플러는 CPU 사용률/누적 CPU 시간, live thread, heap 합계를 resources.csv에 기록한다. GC 이후 힙 값과 순간 CPU 값은 실행 내내 변하므로 한 시점의 값으로 비교하지 않는다.

## 2026-10-09 실행 결과 (KST)

### 자동 테스트

`QUEUE_TEST_REDIS_PORT=16390 .\\gradlew.bat :queue-service:test`에 해당하는 PowerShell 절차 실행:
**23개 통과, 실패 0, 건너뜀 0** (기존 서비스 13 + context 1 + 신규 HTTP/Redis 9).
bootJar 빌드 성공. 운영 소스/빌드 의존성은 변경하지 않았다.

### 로컬 MVC 초기 참고값

- Windows 10 Home, Intel i7-6500U / 4 logical CPUs / 약 7.92 GiB RAM.
- JDK 21.0.5, Redis 7.4.11 (redis:7.4-alpine), k6 0.57.0.
- 앱: 호스트 JVM, -Xms256m -Xmx512m, Tomcat 기본 설정, tracing 비활성.
- Redis 및 k6: 동일 PC의 Docker Desktop. 별도 CPU/메모리 제한 없음.
- 인증 상태와 queue는 같은 테스트 Redis 서버의 DB 0/2. 두 경로 모두 실제 네트워크 I/O.
- admission-size=5, 1초 폴링, READY 후 5초 대기, lifecycle 시나리오.
- 20 VU 스모크 후 같은 JVM에서 100 VU 실행. 별도의 엄격한 warm-up/환경 초기화 없이 측정했으며 이전 테스트 데이터와 스모크 데이터가 일부 남아 있었다.
- 앱/Redis/k6가 CPU를 공유하며 각 조건 1회만 실행했다. 이 수치를 최대 처리량이나 확정된 MVC 대 WebFlux 비교 기준으로 해석하지 않는다. 정식 비교 시 위 절차의 환경 초기화·warm-up·3회 반복을 동일하게 적용한다.

| 항목 | 20 VU / 20초 스모크 | 100 VU / 60초 참고 실행 |
|---|---:|---:|
| 전체 HTTP 요청 수 | 293 | 4,305 |
| 관측 요청률 (graceful stop 포함) | 11.24 req/s | 68.79 req/s |
| HTTP 오류율 | 0% | 0% |
| 업무 check 성공률 | 100% | 100% |
| status 평균 | 206.70ms | 369.71ms |
| status p95 | 471.77ms | 637.05ms |
| status p99 | 481.67ms | 755.04ms |
| 입장 / 완료 | 19 / 19 | 55 / 55 |

100 VU 실행의 자원 샘플 36개 (약 74초, 부하 전후 및 graceful stop 포함):
- live thread 최댓값 126
- JVM heap 최댓값 198.47 MiB (프로세스 RSS/최대 힙 설정값과 다름)
- process_cpu_usage 평균 약 0.23, 최댓값 약 0.52 (JVM 노출 지표, 전체 호스트 CPU 측정값이 아님)
- 누적 CPU 시간 증가 71.375초. 샘플 구간이 부하 실행 구간과 정확히 같지 않으므로 참고값이다.

원본: `results/mvc-smoke/summary.json`, `results/mvc-reference/summary.json`, `results/mvc-reference/resources.csv`.
Redis stats/commandstats는 실행 후 누적 스냅샷으로 저장했으며 해당 실행만의 차분이 아니다.
MODE=waiting 분기는 준비했으나 이번 실측은 lifecycle만 수행했다.
측정 후 전용 앱과 전용 Redis는 종료했다. Docker 이미지는 재실행을 위해 남겨두었다.

다음 단계: 이 테스트를 유지하며 Reactive Redis 기반과 공통 모듈의 MVC 의존성 분리를 준비한다.

WebFlux 전환 후 반복 비교와 다중 인스턴스·Redis 장애 검증은 [5단계 검증 문서](../webflux-validation.md) 및 compare-runtimes.ps1, validate-runtime.ps1을 참고한다.
