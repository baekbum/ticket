# WebFlux 전환 2단계: Reactive Redis 기반

> 이 문서는 전환 당시의 기록이다. 현재 구현과 배포 준비 상태는 [6단계 정리](webflux-finalization.md)를 참고한다.

> 이 문서는 해당 단계 당시의 기록이다. 현재 실행 경로는 [4단계 실행 앱 전환](webflux-runtime.md)을 참고한다.

## 변경 내용

- queue-service에 spring-boot-starter-data-redis-reactive와 reactor-test를 추가했다.
- Boot의 reactiveStringRedisTemplate은 기존 대기열 Redis 설정(spring.data.redis.*)을 사용한다. 기존 StringRedisTemplate과 같은 데이터 형식을 사용한다.
- ReactiveTokenStateStore.get(userId)는 인증 상태를 Mono<TokenState>로 조회한다. 키가 없으면 Mono.empty(), 잘못된 저장값은 오류로 전파한다.
- ReactiveTokenStateConfig는 token-state.redis.* 및 TOKEN_STATE_REDIS_* 환경 변수를 사용한다. 기존 인증 설정과 동일한 2초 command timeout을 적용한다.
- 인증용 LettuceConnectionFactory는 별도 Spring 빈으로 등록하지 않는다. 대기열 Redis의 Boot 자동 설정이 인증 연결로 대체되는 것을 막는다.
- 인증용 연결은 저장소 빈의 close()에서 종료한다.
- common의 실행 의존성에서 starter-web을 제거했다. 공통 코드에 필요한 spring-web은 유지하고, Servlet API는 compileOnly로 사용한다. common 자체 테스트에는 starter-web을 제공한다.
- 모든 다른 실행 앱은 이미 starter-web을 직접 선언하고 있다.
- queue의 common 전체 스캔을 필요한 JwtTokenProvider, TokenStateConfig, LocalCorsConfig 명시적 import로 변경했다. 사용하지 않는 CommonApplication, 감사 로그/Kafka/Slack 컴포넌트가 queue에 자동 등록되지 않는다.

## 현재 실행 상태와 다음 단계

이번 단계의 HTTP 서버와 queue 요청 처리는 기존 MVC/동기 구현이다. Reactive 대기열 템플릿과 인증 조회 저장소를 준비했다.
중간 단계에서는 동기 인증 연결과 Reactive 인증 연결이 함께 생성되며, 실제 JWT 필터는 기존 동기 저장소를 사용한다.

3단계에서 대기열 서비스를 Reactive로 전환한다. 4단계에서 컨트롤러, 인증 필터, 보안/CORS/관측/예외 처리를 전환하고 동기 인증 설정 import 및 MVC 서버 의존성을 제거한다.

Reactive 요청 경로에서 기존 TokenStateStore를 감싸거나 block()으로 결과를 기다리지 않는다.
인증 상태 변경은 기존 auth/user 서비스가 담당한다. queue의 신규 Reactive 저장소는 조회만 제공한다.

## 검증

1단계와 같은 전용 Redis 및 QUEUE_TEST_REDIS_PORT를 사용한다.

~~~powershell
docker run --detach --rm --name ticket-queue-baseline-redis --publish 127.0.0.1:16390:6379 redis:7.4-alpine redis-server --appendonly no
$env:QUEUE_TEST_REDIS_PORT = '16390'
.\gradlew.bat :common:test :queue-service:test --no-daemon --console=plain
.\gradlew.bat compileJava :common:dependencyInsight --dependency spring-webmvc --configuration runtimeClasspath --no-daemon --console=plain
docker stop ticket-queue-baseline-redis
~~~

추가한 실제 Redis 검증:
- Reactive로 저장한 대기열 값을 기존 동기 템플릿으로 읽고, Reactive Lua 실행 및 삭제까지 확인한다.
- 동일 인증 키에 서로 다른 값을 DB 0/2에 저장하여 인증 조회가 DB 0을 읽는지 확인한다.
- 기존 동기 저장소로 인증 상태를 폐기하면 Reactive 조회에도 변경이 반영된다.
- 없는 사용자의 인증 상태는 빈 Mono로 완료된다.

2026-10-09 검증 결과:
- common 테스트 33개, queue 테스트 26개: 모두 통과, 건너뜀 0개.
- 전체 실행 서비스의 compileJava 성공.
- common runtimeClasspath에 spring-webmvc가 없음을 dependencyInsight로 확인.
- 기존 HTTP/인증 계약과 Reactive Redis의 실제 데이터/DB 분리/Lua 호환성 검증 통과.
- 검증용 Redis 컨테이너는 종료했다.
