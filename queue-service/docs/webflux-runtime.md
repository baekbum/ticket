# WebFlux 전환 4단계: 실행 앱 전환

## 실제 요청 경로

queue-service의 starter-web을 starter-webflux로 교체했다. 실행 서버는 Reactor Netty이며,
일반 사용자 및 관리자 컨트롤러가 Reactive 서비스를 호출한다.

브라우저 → Ingress → Kubernetes Service → queue Pod의 Netty → WebFlux 보안 필터
→ DispatcherHandler → 컨트롤러 → Reactive 서비스 → Lettuce → Redis 순서로 처리한다.

컨트롤러는 Mono<ResponseEntity<...>>를 반환한다. WebFlux가 구독과 HTTP 응답을 관리하며,
서비스와 필터에서 block()이나 직접 subscribe()를 호출하지 않는다.

## 변경한 구성

- QueueController: 등록·상태·이탈·검증·완료를 ReactiveQueueService에 연결했다.
- QueueRedisManagementController: 조회와 삭제를 ReactiveQueueRedisInspectService에 연결했다.
- QueueTestManagementController: Reactive 관리자 서비스를 사용하고 직접 동기 Redis 호출을 제거했다.
- SecurityConfig: HttpSecurity와 Servlet 필터 대신 ServerHttpSecurity와 SecurityWebFilterChain을 사용한다.
- ReactiveJwtAuthenticationFilter: JWT와 별도 인증 Redis의 상태를 검증하고 Reactor Context로 인증 정보를 전달한다.
- ReactiveLocalCorsConfig: 공통 MVC 설정과 같은 로컬 Origin·헤더·메서드·자격 증명 규칙을 제공한다.
- GlobalExceptionHandler: WebExchangeBindException을 기존 VALIDATION_FAILED 응답으로 변환한다.
- ObservabilityConfig: Reactive HTTP 관측 컨텍스트를 사용해 actuator 요청을 제외한다.
- ReactiveQueueService와 ReactiveQueueAdminService: reactor-core-micrometer의 tap으로 실제 구독 수명을 관측한다.
- application.yaml: cleanup-mode=reactive를 기본으로 설정한다. prod 프로필도 기본 설정을 상속한다.
- spring.reactor.context-propagation=auto: 스레드 전환 후에도 관측 컨텍스트와 로그 traceId를 전달한다.

QueueServiceApplication은 공통 JwtTokenProvider만 가져온다.
공통 Servlet CORS와 동기 TokenStateConfig는 실행 앱에서 가져오지 않는다.
인증 Redis와 대기열 Redis의 연결·DB 분리는 기존 ReactiveTokenStateConfig가 유지한다.

JWT 필터는 SecurityWebFilterChain 안에만 넣으며 별도 전역 WebFilter 빈으로 등록하지 않는다.
인증 저장소 오류만 503으로 변환하고, 인증 이후 서비스에서 발생한 오류는 서비스 오류로 전달한다.
보안 컨텍스트와 요청 캐시는 세션에 저장하지 않으며 HTTP Basic·폼 로그인·로그아웃을 비활성화한다.

## API와 인증 계약

URL, 요청 DTO, 헤더, 응답 필드, Redis 키 및 토큰 형식은 그대로 유지한다.
사용자 요청에 따라 인증 오류 문구는 한국어로 변경했다. HTTP 상태 코드와 JSON 구조는 유지한다.

| 조건 | 응답 |
|---|---|
| 인증 정보 없는 보호 경로 | 403 |
| Refresh 토큰을 Access 토큰으로 사용 | 401 / 액세스 토큰이 필요합니다. |
| 만료된 JWT | 401 / 액세스 토큰이 만료되었습니다. 다시 로그인해주세요. |
| 잘못되거나 변조된 JWT | 403 / 유효하지 않거나 변조된 토큰입니다. |
| 인증 상태 없음·비활성·버전 또는 역할 불일치 | 401 / 인증이 만료되었거나 취소되었습니다. 다시 로그인해주세요. |
| 인증 Redis 조회 실패 | 503 / 인증 상태를 확인할 수 없습니다. 잠시 후 다시 시도해주세요. |
| USER의 관리자 API 호출 | 403 |
| validate/complete | 기존처럼 인증 없이 호출 가능 |

관리자 API는 ADMIN 권한을 요구한다.
로컬 프로필에서만 앱이 CORS를 처리하며, prod의 앱 CORS 비활성화 규칙을 유지한다.
승격 시 대기 토큰 소비, 세션 제한 확인, 강제 교체, 관리자 삭제 범위도 이전 계약과 같다.

## 관측

기존 queue.enter, queue.status, queue.validate-token, queue.complete, queue.leave,
queue.admin.statuses 이름을 유지한다.

Reactive 메서드에 단순히 @Observed를 붙이면 Mono를 구성하고 반환하는 시간만 관측할 수 있으므로,
Reactor 관측 연산자를 사용해 구독에서 완료·오류·취소까지 측정한다.
기존 동기 서비스의 ObservedAspect는 비교 검증용 구현이 남아 있는 동안 유지한다.

참고:
- https://projectreactor.io/docs/core/release/reference/metrics.html
- https://docs.spring.io/spring-boot/3.4/reference/actuator/observability.html

## 검증 방법

~~~powershell
docker run --detach --rm --name ticket-queue-baseline-redis --publish 127.0.0.1:16390:6379 redis:7.4-alpine
$env:QUEUE_TEST_REDIS_PORT = '16390'
.gradlew.bat :queue-service:test :common:test :queue-service:bootJar --no-daemon --console=plain
docker stop ticket-queue-baseline-redis
~~~

QUEUE_TEST_REDIS_PORT를 지정하지 않으면 Redis 통합 테스트는 건너뛴다.

실제 Netty 서버에서 기존 HTTP 계약, 사용자 권한, 관리자 등록·상태·일괄 조회·검사·삭제·완료,
로컬 CORS 사전 요청을 확인한다. HTTP 테스트의 동기 TokenStateConfig는 인증 데이터 준비용으로만 가져온다.
Prometheus 테스트에는 AutoConfigureObservability를 명시해 테스트 환경의 메트릭 내보내기를 활성화한다.

필터 테스트는 인증 정보의 구독 시점 조회, 비동기 처리 후 인증 컨텍스트 전달,
인증 Redis 장애, 토큰 거부 유형과 후속 서비스 오류의 보존을 확인한다.
관측 테스트는 구독 전 미실행 및 오류·취소 시 관측 종료를 확인한다.
기존 스케줄러 테스트는 Reactive 정리만 실행하고 작업이 겹치지 않는지 확인한다.

## 남은 단계

기존 동기 서비스는 비교 테스트를 위해 남아 있지만 기본 HTTP·인증·정리 경로에서는 사용하지 않는다.
동기 Redis 라이브러리도 이 비교 구현과 테스트 때문에 아직 유지한다.

5단계에서 부하 비교, 요청 취소·응답 유실·Redis 장애와 복구, 여러 인스턴스의 정합성을 검증한다.
만료 정리의 부분 실패, 세션 인덱스 정리, 이벤트 간 강제 세션 교체 등 기존 제약은 이 전환에서 수정하지 않았다.
기존 구현과 필요 없어진 의존성 제거는 6단계에서 진행한다.

## 2026-10-11 검증 결과

- queue-service 테스트 54개 통과, 실패·오류·건너뜀 0개.
- common 테스트 33개 통과, 실패·오류·건너뜀 0개. 변경이 없는 common 테스트는 Gradle의 기존 성공 결과를 재사용했다.
- 전체 서비스 compileJava 및 queue-service bootJar 성공.
- 실제 HTTP 서버가 ReactiveWebServerApplicationContext와 NettyWebServer임을 확인했다.
- 실행 JAR에 spring-webflux와 reactor-netty-http가 포함되고 spring-webmvc와 tomcat-embed-core가 없는 것을 확인했다.
  Bean Validation의 표현식 처리를 위한 tomcat-embed-el은 서버 라이브러리가 아니므로 유지한다.
- 실제 Netty 서버로 CORS 사전 요청을 전송해 허용 출처의 200 및 미허용 출처의 403을 확인했다.
- Prometheus에서 HTTP 요청과 queue.enter 서비스의 메트릭 노출을 확인했다.
- 검증 후 전용 Redis 컨테이너를 종료했다.

초기 CORS 테스트는 상대 URI로 만든 모의 요청 때문에 실패했으며, 실제 서버 주소로 전송하도록 테스트를 수정했다.
Prometheus 테스트는 Spring Boot 테스트의 기본 내보내기 비활성화로 404가 발생했으며,
AutoConfigureObservability를 명시한 뒤 통과했다. 이 두 건 때문에 운영 CORS나 인증 규칙을 완화하지 않았다.
