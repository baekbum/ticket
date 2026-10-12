# WebFlux 전환 5단계 검증

이 문서는 2026-10-11 검증 당시의 결과와 계약을 보존한다.
2026-10-12에 승격 결과 복구 기능을 추가하여 유효한 세션의 같은 대기 토큰 재조회는 200으로 변경했다.
현재 계약과 검증은 [대기 토큰을 사용한 승격 결과 복구](queue-admission-idempotency.md)를 참고한다.
아래의 재조회 400 및 멱등 API 미추가 설명은 이 변경 전 상태를 뜻한다.

## 정합성 보완

Reactive 구현에서 아래 기존 제약을 재현하고 수정했다.

- 동일 사용자 동시 등록: 한도 조회와 등록 사이의 경쟁으로 최대 세션 수를 넘었다. Lua에서 한도 확인과 대기열·만료 인덱스·토큰·사용자 세션 등록을 함께 처리한다. 경쟁에서 한도에 도달하면 기존 SESSION_LIMIT_CONFIRM_REQUIRED 응답을 사용한다.
- 승격 이후 갱신: 삭제된 대기 토큰을 SET으로 다시 생성했다. 갱신 스크립트에서 토큰 소유자와 대기열 멤버를 확인하며, 이미 처리된 토큰은 400으로 거부한다.
- 다른 이벤트로 강제 등록: 새 요청의 이벤트에서 이전 토큰을 제거했다. 토큰 값에서 원래 이벤트를 찾아 대기 또는 활성 인덱스를 제거한다. 강제 교체 전체는 하나의 트랜잭션이 아니므로 삭제 후 등록 실패 시 이전 세션을 복원하지 않는다. 동시에 교체하면 최종 등록에서 한도 응답이 나올 수 있다.
- 만료 정리: 여러 Redis 명령 중간의 실패 및 조회·삭제 사이의 갱신 경쟁이 있었다. 토큰별 삭제를 스크립트로 묶고 조회 시점의 고정된 만료 기준을 다시 확인한다. 활성 정리도 조회한 개별 토큰만 삭제한다.
- 잘못된 Redis 키 타입은 각 변경 스크립트가 쓰기 전에 확인한다. 오류가 발생하면 만료 인덱스를 유지해 데이터 타입을 복구한 뒤 재시도할 수 있다.

Lua는 다른 명령이 중간에 끼어드는 것을 막지만, 실행 중 발생한 모든 오류를 자동으로 롤백하지는 않는다.
타입 검사는 검증한 오류를 막는 조치이며, Redis 메모리 부족·강제 종료·네트워크 응답 유실까지 되돌린다는 의미는 아니다.
스크립트 실행이 성공한 직후 연결이 끊어지면 클라이언트는 성공 여부를 확정하지 못할 수 있다. 변경 요청을 무조건 자동 재시도하지 않는다.

토큰 TTL이 이미 지나 소유자 값을 읽을 수 없으면 이벤트 인덱스만 정리한다.
사용자 세션 인덱스의 잔여 멤버는 해당 사용자의 다음 등록 시 prune으로 정리한다.
만료 인덱스만으로 소유자를 복원하는 새 Redis 스키마는 도입하지 않았다.

## 첫 요청의 블로킹 제거

BlockHound를 적용한 실제 Netty HTTP 테스트에서 두 블로킹 경로를 발견했다.

1. JWT 파서 생성 중 ServiceLoader가 JAR 파일을 읽었다. 공통 JwtTokenProvider가 앱 시작 시 불변 파서를 생성하고 재사용한다.
2. Lettuce 공유 연결의 최초 생성이 연결 완료를 기다렸다. 대기열 및 인증 Redis에 eager initialization을 적용한다. 대기열 연결은 모든 빈 생성 이후 Lifecycle 단계에서 준비해 Prometheus의 지연 tracing 초기화와 빈 생성이 서로 기다리지 않도록 한다.

Lua 텍스트와 SHA도 앱 시작 시 메모리에 준비해 요청 중 리소스 파일을 읽지 않도록 한다.
Redis는 앱 시작 시 접근 가능해야 한다. Redis가 준비되지 않으면 시작이 실패할 수 있으며, 배포 시 의존 서비스 준비 순서를 반영해야 한다.
기존 서버/CORS context 테스트도 이제 테스트 Redis 포트를 지정했을 때 실행한다.

- [JJWT 0.11.5 불변·스레드 안전 파서](https://javadoc.io/static/io.jsonwebtoken/jjwt-api/0.11.5/io/jsonwebtoken/JwtParserBuilder.html)
- [Spring Data Redis 3.4.1 eager initialization](https://docs.spring.io/spring-data/redis/docs/3.4.1/api/org/springframework/data/redis/connection/lettuce/LettuceConnectionFactory.html)
- [BlockHound 설정](https://github.com/reactor/BlockHound)

일반 테스트에는 동기 비교 서비스와 Redis fixture가 있으므로 BlockHound 검사는 별도 nonBlockingTest JVM에서 실행한다.
블로킹을 허용하는 예외 목록은 추가하지 않는다.
탐지기가 Thread.sleep을 실제로 거부하는지 확인하고 HTTP 인증·등록·승격·대기 갱신·탈퇴·완료와 Reactive 정리 구독을 검사한다.
검증된 경로 외의 모든 코드에 블로킹이 전혀 없다는 보장은 아니다.

## Redis 장애와 요청 취소

대기열 Redis 명령 제한 시간은 SPRING_DATA_REDIS_TIMEOUT으로 설정하며 기본은 2초다.
인증 Redis의 기존 명령 제한 시간도 2초다.
대기열의 DataAccessException은 503과 QUEUE_UNAVAILABLE 한국어 메시지로 응답한다.
명령 제한 시간은 요청 전체의 절대 제한 시간이 아니며, 여러 명령과 연결 재수립 시간을 모두 포함한 SLA를 보장하지 않는다.

등록 완료 직후 응답 파이프라인을 취소해도 Redis 변경은 롤백되지 않는다.
테스트는 대기열·만료 인덱스·토큰·사용자 세션이 함께 등록되어 있고 TTL이 양수인지 확인한다.
TCP 패킷 손실의 모든 시점과 연결 종료 타이밍을 자동 테스트로 재현한 것은 아니다.

승격 응답이 유실된 클라이언트가 같은 대기 토큰으로 status를 재시도하면 현재 계약은 400이다.
활성 세션은 하나만 존재하고 TTL까지 슬롯을 점유할 수 있다.
응답 유실 후 활성 토큰을 다시 알려주는 멱등 API는 별도 계약·스키마 설계가 필요하며 이번 단계에서는 추가하지 않았다.

## 재현

~~~powershell
docker run --detach --rm --name ticket-queue-baseline-redis --publish 127.0.0.1:16390:6379 redis:7.4-alpine
$env:QUEUE_TEST_REDIS_PORT = '16390'
.\gradlew.bat :queue-service:test :queue-service:nonBlockingTest :common:test :queue-service:bootJar --no-daemon --console=plain
docker stop ticket-queue-baseline-redis

& queue-service/docs/baseline/validate-runtime.ps1 -Jar queue-service/build/libs/queue-service-0.0.1-SNAPSHOT.jar
& queue-service/docs/baseline/compare-runtimes.ps1 -MvcJar <MVC 실행 JAR 경로> -WebFluxJar queue-service/build/libs/queue-service-0.0.1-SNAPSHOT.jar
~~~

validate-runtime.ps1는 포트 18084/18085의 두 JVM과 전용 Redis 16401을 생성한다.
동일 사용자 24개 동시 등록, 서로 다른 사용자 12명의 반대 인스턴스 상태 조회,
Redis pause/unpause 중 인증 및 대기열의 503, 복구 후 등록, 승격 응답 유실 계약을 검증한다.
스크립트가 생성한 프로세스와 컨테이너만 종료한다.

compare-runtimes.ps1는 MVC/WebFlux 실행 순서를 교차하며 기본 3회씩 비교한다.
각 실행에 새 Redis 16400, 새 JVM, -Xms256m -Xmx512m, 20 VU/20초 warm-up 및 100 VU/60초 lifecycle 측정을 적용한다.
warm-up은 별도 사용자·이벤트를 쓰고 같은 JVM의 측정 배경 데이터로 유지한다.
기존 sample-resources.ps1의 약 2초 주기 JVM 지표와 Redis 명령 통계 전후, 앱 CPU 시간, 종료 시점 working set을 저장한다.
working set은 실행 중 최대 RSS가 아니다. CPU 시간 구간은 사용자 준비 및 k6 시작·종료를 포함한 실행 구간이다.

## 부하 해석의 범위

비교 대상 MVC 소스는 e66cdc3이며, WebFlux는 이번 단계의 수정까지 포함한다.
JWT 파서 재사용과 Redis 스크립트 변경도 함께 적용되므로 결과를 웹 서버 방식 하나의 효과로 분리할 수 없다.
같은 PC에서 JVM·Docker Redis·k6가 CPU를 공유한다. 닫힌 부하 모델이며 운영 최대 처리량을 측정하지 않는다.
호스트 전체 CPU 및 운영 규모의 Redis 지연, 여러 대의 서버에 대한 부하 측정은 포함하지 않는다.
## 2026-10-11 자동 테스트 결과

- queue 일반 테스트 63개, nonBlockingTest 2개, common 테스트 33개 통과.
- 실패·오류·건너뜀 0개. 변경이 없는 common의 마지막 재실행은 앞선 성공 결과를 재사용했다.
- queue 실행 JAR 빌드 성공.
- 최초 회귀 테스트 3개가 기존 구현에서 실패한 뒤 수정 후 통과했다.
- 오류용 키 타입 fixture가 후속 전체 SCAN 정리에 섞인 문제는 테스트 종료 시 자신의 이벤트 키를 제거하도록 수정했다.
- BlockHound가 JWT 파서 생성과 Redis 첫 연결의 블로킹을 검출했고, 실제 HTTP 첫 요청과 대기 갱신·탈퇴·완료까지 수정 후 통과했다.
- 관측 기능을 켠 HTTP 테스트에서 Redis 조기 연결과 Prometheus tracing 빈 조회의 순환 대기를 확인해 Lifecycle 연결 초기화로 수정했다.
## 실제 두 인스턴스 및 Redis 일시 중단 결과

두 JVM(18084/18085)이 같은 전용 Redis 서버(16401, queue DB 2, 인증 DB 0)를 사용했다.

- 동일 사용자 동시 등록 24건: 등록 2건, 세션 한도 응답 22건, Redis 사용자 세션 2개.
- 서로 다른 사용자 12명: 등록과 반대 인스턴스의 상태 조회 성공, READY 2명, WAITING 10명. Redis 활성 2개·대기 10개와 일치.
- Redis pause 중 인증 요청: 약 2.194초 후 503과 한국어 인증 상태 오류.
- Redis pause 중 인증 없는 validate: 약 2.377초 후 503, QUEUE_UNAVAILABLE.
- unpause 후 등록: 200/WAITING으로 복구.
- 승격 응답을 유실한 것으로 취급하고 같은 대기 토큰으로 재조회: 400, 활성 세션 1개, 잔여 TTL 1,199초. 현재 비멱등 계약을 확인했다.
- 검증용 두 앱과 Redis 컨테이너 종료 완료.

원본: docs/baseline/results/step5/validation/{summary.json,validation.json,app-18084.log,app-18085.log}.
pause는 연결을 유지한 Redis 응답 지연을 재현한다. Redis 프로세스 재시작·데이터 유실·failover·네트워크 단절 전체를 검증한 것은 아니다.
부하 비교의 첫 준비 실행은 다음 Redis 생성과 --rm 제거가 겹쳐 중단됐다.
컨테이너 제거 완료 대기를 추가한 뒤 comparison 디렉터리에서 다시 실행했으나 첫 MVC 측정의 성공 기준 실패로 중단됐다. 이후 임계치 실패도 보존하도록 실행기를 보완하고 comparison-complete에서 6회 비교를 완료했다.
중단된 준비 실행의 mvc-1 결과는 정식 비교에 포함하지 않는다.
## 동일 조건 3회 부하 비교

Intel i7-6500U / 4 logical CPUs / 약 7.92 GiB RAM, Windows, JDK 21.0.5.
Redis 7.4-alpine 및 k6 0.57.0이 JVM과 같은 PC에서 실행됐다.
admission-size=5, 1초 폴링, READY 후 5초 대기, 20 VU/20초 warm-up, 100 VU/60초 측정 및 graceful stop 15초.
측정 순서는 MVC1 → WebFlux1 → WebFlux2 → MVC2 → MVC3 → WebFlux3이다.

| 실행 | HTTP 요청 수 | req/s | status p95(ms) | status p99(ms) | HTTP 오류율 | 입장/완료 |
|---|---:|---:|---:|---:|---:|---:|
| mvc-1 | 3711 | 59.22 | 1,914.12 | 2,414.14 | 0.00% | 50/50 |
| mvc-2 | 3550 | 54.14 | 1,594.69 | 1,780.71 | 0.00% | 50/50 |
| mvc-3 | 3804 | 59.24 | 735.62 | 1,628.96 | 0.00% | 50/50 |
| webflux-1 | 4786 | 72.62 | 715.14 | 1,588.62 | 0.00% | 53/53 |
| webflux-2 | 4726 | 71.62 | 962.09 | 1,385.45 | 0.00% | 52/52 |
| webflux-3 | 4739 | 72.12 | 694.89 | 1,330.55 | 0.00% | 54/54 |

6회 모두 업무 check 성공률 100%, k6 임계치 통과였다.
위 req/s는 k6가 기록한 graceful stop 포함 비율이다.

| 실행 | 최대 JVM 스레드 | 최대 샘플 heap(MiB) | 앱 CPU 시간(초) | 종료 시 working set(MiB) |
|---|---:|---:|---:|---:|
| mvc-1 | 128 | 187.91 | 71.64 | 458.52 |
| mvc-2 | 127 | 181.97 | 74.33 | 486.39 |
| mvc-3 | 127 | 187.17 | 77.78 | 466.86 |
| webflux-1 | 34 | 174.50 | 63.53 | 465.01 |
| webflux-2 | 34 | 173.74 | 63.92 | 465.43 |
| webflux-3 | 34 | 170.34 | 61.50 | 461.15 |

회차별 지표의 중앙값:
- status p95: MVC 1,594.69ms, WebFlux 715.14ms.
- status p99: MVC 1,780.71ms, WebFlux 1,385.45ms.
- 요청률: MVC 59.22 req/s, WebFlux 72.12 req/s.
- 최대 JVM 스레드: MVC 127개, WebFlux 34개.
- 앱 CPU 시간: MVC 74.33초, WebFlux 63.53초.
- 최대 샘플 heap: MVC 187.17 MiB, WebFlux 173.74 MiB.

위 중앙값은 세 실행의 개별 p95/p99 등의 중앙값이다. 모든 요청을 합쳐 계산한 분위수가 아니다.
앱 CPU 시간은 사용자 준비와 k6 컨테이너 시작·종료를 포함한 약 74~78초 구간이다.
working set은 종료 시점의 프로세스 메모리이며 실행 중 최대값이 아니다.
JVM heap이나 working set이 스레드 수만큼 감소한 결과는 아니었다.

WebFlux에서 스레드 감소는 일관적이었다. 로컬 반복 결과의 지연·요청률·CPU도 개선됐으나,
MVC p95가 735.62~1,914.12ms로 크게 변했고 WebFlux p95도 694.89~962.09ms 범위였다.
JWT 파서 재사용과 Redis 왕복 감소가 함께 포함되며, 웹 서버 변경 단독 효과나 운영 최대 처리량으로 일반화하지 않는다.
입장/완료 횟수는 슬롯 5개와 체류 시간 정책의 제한도 받으므로 HTTP 처리량과 구분한다.

정식 6회 비교 이전의 comparison/mvc-1 측정은 HTTP 2,278건 중 39건 실패(약 1.712%), 업무 검사 약 98.288%로 성공 기준을 넘지 못했다.
당시 실패 응답의 상태/본문은 수집하지 않아 원인을 확정할 수 없다. 앱 오류 로그에도 원인을 확인할 기록이 없었다.
이 실패를 기록에서 제거하지 않는다. 해당 실행은 비교 실행기가 임계치 오류로 중단해 CPU 결과와 짝 WebFlux 실행이 없으므로 위 6회 표와 별도로 보존한다.
이후 VU당 최초 실패 응답을 수집하도록 k6 스크립트를 보완했고, k6의 임계치 실패(99)도 결과로 남기고 다음 측정을 계속하도록 했다.
정식 6회에서는 이 실패가 재현되지 않았다. 따라서 안정성을 무조건 보장하는 결과로 해석하지 않는다.

원본:
- docs/baseline/results/step5/comparison-complete/{mvc-1,mvc-2,mvc-3,webflux-1,webflux-2,webflux-3}
- 각 디렉터리의 summary.json, resources.csv, cpu.json, manifest.json, Redis 통계 전후 및 앱 로그.
- comparison-summary.json, step5/environment.json에 비교 수치와 실행 JAR SHA-256·환경을 보존했다.
- results 디렉터리는 Git에서 제외하며 이 문서에 핵심 수치를 기록한다.

## 다음 단계

6단계에서 비교용 동기 서비스와 불필요한 의존성을 정리한다.
이번 단계에서 바뀐 Redis 시작 의존성과 장애 응답을 배포 설정·운영 문서에 반영한다.
승격 응답 유실 복구를 위한 멱등 API, 만료된 사용자 세션 인덱스의 전역 정리 및 운영 환경 규모의 부하는 별도 설계·검증 범위다.