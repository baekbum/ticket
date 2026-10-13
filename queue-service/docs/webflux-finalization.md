# Queue WebFlux 전환 6단계: 기존 구현 정리와 배포 준비

2026-10-13 기준. 실행 앱은 기존 queue-service이며 HTTP 서버는 WebFlux/Netty다.

## 정리한 코드

- 동기 QueueService, ActiveQueueService, WaitingQueueService, UserQueueSessionService, QueueAdminService, QueueRedisInspectService와 QueueCleanupScheduler를 제거했다.
- 동기 승격 전용 queue-admit.lua와 스크립트 상수·시작 시 초기화를 제거했다. 실제 승격에는 멱등 처리가 포함된 queue-status.lua를 사용한다.
- 동기 서비스용 ObservedAspect와 queue-service의 AOP starter를 제거했다. Reactive 구독의 성공·오류·취소 관측은 Micrometer tap으로 유지한다.
- queue-service에서 중복 선언한 동기 Redis starter를 제거했다. common은 다른 MVC 서비스가 사용하는 Redis API를 제공하므로 common의 동기 Redis 의존성은 유지한다. 실행 JAR에 동기 Redis API 클래스가 존재할 수 있으나 Queue 운영 코드에서는 호출하지 않는다.
- 동기 서비스의 Mockito 테스트 13개를 제거했다. 기존 Redis 키·토큰 형식 호환성은 실제 Redis fixture로 검증하도록 바꿨다.
- 등록·대기·승격·완료·탈퇴·세션 한도·강제 교체·관리자 조회·삭제·동시성·멱등 처리·장애·블로킹 탐지 테스트는 유지한다.
- 사용하지 않는 Reactive 등록 보조 메서드와 예외 생성 메서드를 제거했다.
- 과거 MVC 비교 자료와 부하 측정 도구는 보존했다. compare-runtimes.ps1에는 과거 Git 버전에서 빌드한 MVC JAR을 별도로 전달해야 한다. 현재 소스로 MVC 앱을 실행할 수는 없다.

## 실행 및 배포 설정

| 항목 | 설정과 확인 사항 |
|---|---|
| 로컬 포트 | 8083. 로컬 ingress는 호스트의 8083으로 전달한다. |
| 운영 포트 | prod 프로필에서 8080. 운영 ingress는 queue-service:8080으로 전달한다. |
| 대기열 Redis | SPRING_DATA_REDIS_HOST / PORT / DATABASE. prod 기본 DB는 0이며 인증 Redis와 다른 서버를 기본으로 사용한다. |
| 대기열 명령 제한 시간 | SPRING_DATA_REDIS_TIMEOUT, 기본 2s |
| 인증 Redis | TOKEN_STATE_REDIS_HOST / PORT / DATABASE / PASSWORD. prod 기본 refresh-redis:6379, DB 0 |
| 인증 명령 제한 시간 | TOKEN_STATE_REDIS_TIMEOUT, 기본 2s. 이번 단계에서 조절 가능하게 변경했다. |
| 활성 토큰 TTL | APP_QUEUE_ACTIVE_TOKEN_TTL. Compose 기본 10m, prod YAML 기본 20m. 기존 APP_QUEUE_TOKEN_TTL fallback은 호환성을 위해 유지한다. |
| 대기 토큰 TTL | APP_QUEUE_WAITING_TOKEN_TTL, 기본 1m |
| 정리 스케줄러 | app.queue.cleanup-mode=reactive 또는 설정 생략 시 실행. disabled이면 중지. blocking 모드는 더 이상 제공하지 않는다. |
| 정리 주기 | APP_QUEUE_CLEANUP_FIXED_DELAY_MS, 기본 20000ms. 한 실행의 완료 후 다음 주기를 계산한다. |
| 헬스 체크 | /actuator/health. 시작 시 Redis 연결을 준비하므로 Redis가 준비되지 않으면 앱 시작이 실패할 수 있다. |
| 메트릭 | /actuator/prometheus. HTTP와 Reactive Queue 관측을 유지한다. |
| 인증 비밀키 | TOKEN_SECRET. Compose 실행 시 반드시 제공한다. |

queue-service/docker-compose.yml에는 인증 Redis 연결과 양쪽 Redis 타임아웃을 명시했다. 기존 ticket-network, queue-redis, refresh-redis를 사용한다. 이 단계에서 실제 운영 컨테이너를 교체하거나 배포하지 않는다.

Lua는 같은 Redis 서버의 다른 명령 실행을 기다리게 할 수 있으며 WebFlux는 애플리케이션의 I/O 대기 비용을 줄인다.

기존 waiting/active 토큰의 키와 값 형식은 유지한다. 배포 이전에 이미 승격된 활성 토큰은 검증·완료가 가능하지만, 당시 생성되지 않았던 waiting→active 매핑을 소급해서 복원할 수는 없다. 대기열 Redis를 비우는 작업은 필요하지 않다.

## 재현 방법

전용 Redis를 준비하고 QUEUE_TEST_REDIS_PORT를 설정한다. 테스트는 지정된 Redis DB를 사용하므로 운영 Redis를 전달하지 않는다.

    docker run --rm -d --name ticket-queue-stage6-redis -p 127.0.0.1:16391:6379 redis:7.4-alpine
    $env:QUEUE_TEST_REDIS_PORT = '16391'
    ./gradlew.bat :common:test :queue-service:clean :queue-service:check :queue-service:bootJar --offline --no-daemon --console=plain
    docker stop ticket-queue-stage6-redis

테스트 Redis 미지정으로 통합 테스트가 건너뛰어진 결과는 최종 검증으로 인정하지 않는다. check는 별도 JVM의 BlockHound 검사도 실행한다.

    & queue-service/docs/baseline/validate-runtime.ps1 -Jar queue-service/build/libs/queue-service-0.0.1-SNAPSHOT.jar -Profile prod -OutputDirectory queue-service/docs/baseline/results/step6/validation

이 스크립트는 별도 Redis와 JVM 두 개를 생성하고 종료한다. prod 설정을 로드하되 로컬 연결·테스트 비밀키·슬롯 2개·세션 한도 2개를 덮어쓰고 OTLP 외부 전송을 피하기 위해 tracing을 비활성화한다. 운영 네트워크·ingress·인증 Redis의 실제 계정 설정과 외부 OTLP 수집기까지 검증하는 배포 테스트는 아니다.

## 최종 검증 결과

- queue-service 일반 테스트 71개와 별도 BlockHound 테스트 2개, 총 73개 통과. 실패·오류·건너뜀 0개. 기존 84개에서 동기 단위 테스트 13개를 제거하고 스케줄러 기본 실행·비활성화 테스트 2개를 추가한 결과다.
- common:test는 변경 없는 이전 성공 결과를 Gradle에서 재사용했다.
- queue-service:clean / check / bootJar 성공.
- 실행 JAR에 spring-webflux와 reactor-netty-http가 포함되어 있고, spring-webmvc / tomcat-embed-core / aspectjweaver / 기존 동기 서비스 / queue-admit.lua가 없음을 확인했다.
- Docker Compose 설정 및 검증 PowerShell 스크립트 구문 검사 통과.
- prod 프로필 앱 두 개의 헬스 체크 UP 및 Netty 시작 확인.
- 동일 사용자 등록 24건 중 2건 WAITING, 22건 세션 한도 응답. 사용자 세션 2개 유지.
- 별도 사용자 status 12건 중 2건 READY, 10건 WAITING. 활성 슬롯 2개 유지.
- 같은 대기 토큰의 재요청 24건이 모두 같은 활성 토큰과 최초 만료 시각을 반환. 활성 슬롯 1개 유지.
- Redis 일시 정지 시 인증 요청 503(약 2.16초), 대기열 조회 503(약 3.32초). 복구 후 등록 200. 설정된 명령 제한 시간 외의 HTTP·스케줄링 시간이 포함되므로 요청 전체가 정확히 2초라고 보장하지 않는다.
- 응답 유실 후 재요청 200, 완료 후 재요청 400, 양방향 매핑 키 0개.
- 생성한 JVM 두 개와 전용 Redis 두 개를 검증 후 종료했다.

원본은 docs/baseline/results/step6/validation에 보관하며 Git에는 포함하지 않는다. 실제 운영 배포와 Docker 이미지 빌드는 수행하지 않았다.

이전 성능 결과는 [5단계 기록](webflux-validation.md), 승격 재요청의 현재 계약은 [멱등 처리 문서](queue-admission-idempotency.md)를 참고한다. 이번 정리 단계에서 성능 비교를 다시 수행하지 않으므로 이전 수치를 현재 코드의 재측정 결과로 해석하지 않는다.