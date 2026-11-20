# 토큰 버전과 계정 세션 폐기

Access Token은 15분, Refresh Token은 14일 동안 유효하다. AUTH DB의 `auth.token_version`은 1부터 시작하며 로그인·재발급에서는 증가하지 않는다. 두 JWT에 현재 버전과 `tokenType`(`access`/`refresh`)을 넣는다.

USER → Kafka → AUTH 동기화는 유지한다. AUTH는 비밀번호, 권한, 탈퇴 상태, 차단 상태가 실제로 변경되면 버전을 증가시킨다. 비밀번호 재설정 이벤트에서만 기존 로그인 실패 카운트와 잠금을 초기화하는 정책도 유지한다.

AUTH DB 커밋 후 인증용 Redis의 `AUTH:STATE:{userId}`에 `버전:활성여부:권한`을 게시한다. 이전 버전의 `RT:{userId}` 삭제와 상태 갱신은 하나의 Lua 연산으로 실행한다. 현재 정보와 동일한 수정 이벤트의 재전달은 버전을 다시 증가시키거나 새 로그인 세션을 삭제하지 않는다. DB 커밋 후 Redis 갱신에 실패하면 Kafka 재시도로 현재 DB 버전을 다시 게시한다.

AUTH의 `/api/v1/validate`와 모든 서비스의 공통 JWT 필터는 서명·만료·토큰 종류를 확인하고 Redis의 현재 버전, 계정 활성 여부, 권한과 대조한다. 폐기된 토큰이나 계정 상태 누락은 401, 인증 상태 저장소 장애는 503으로 거부한다. 실패 시 기존 JWT만으로 인증을 허용하지 않는다.

재발급과 로그인은 Refresh Token을 저장하는 Lua 연산 안에서도 현재 상태를 다시 확인한다. 상태 변경 후 진행 중이던 이전 버전 요청이 토큰을 복원할 수 없다. 물리 삭제 후 동일 사용자 ID로 재가입한 계정은 Redis에 남은 이전 계정 버전보다 높은 버전으로 시작한다.

## 사용자 화면의 인증 만료 처리

2026-11-19. Access Token 만료의 401은 자동 재발급으로 복구한다. Refresh Token도 없거나 재발급이 401/403으로 거절되면 Access/Refresh Token과 사용자명을 함께 지우고 로그인 화면으로 이동하며, 세션이 만료되었으니 다시 로그인해달라는 안내를 표시한다. 이미 토큰이 사라진 보호 API 요청도 익명 요청을 보내지 않고 같은 전환을 수행한다.

403 전체를 로그아웃으로 취급하지 않는다. INVALID_TOKEN·TOKEN_EXPIRED 등 인증 실패 코드 또는 공통 JWT 필터의 명시적 인증 오류만 만료로 분류한다. 일반 권한 부족 403과 일시적인 인증 저장소 503·통신 오류는 로그인 정보를 유지한다. 지연된 이전 토큰의 403은 새 로그인 토큰을 지우지 않는다.

동시 실패는 현재 창에서 만료 안내를 한 번만 발행한다. 하위 화면의 최초 요청 전에 만료 알림을 구독하여 마이티켓 첫 조회가 즉시 실패해도 헤더 사용자명과 실제 인증 상태가 엇갈리지 않게 한다. 예매 active-token은 삭제하지 않는다.

좌석 팝업은 자신을 열어 준 창과 같은 로그인 세션에 속하는 동안 그 창의 `sessionStorage`에 있는 최신 로그인 토큰과 진행 중인 재발급을 공유한다. 대기열 처리 중 토큰이 갱신돼도 첫 좌석 조회에서 최신 토큰을 사용한다. 메인 창에서 새로 로그인하면 이전 세션의 좌석 팝업은 새 토큰에 접근할 수 없다.

회귀 테스트는 동시 401의 단일 갱신, 갱신 거절·토큰 누락, 인증 403/권한 403 구분, 일시 오류의 토큰 보존과 실제 React 마이티켓→로그인 전환을 검증한다.

## 배포

1. 기존 AUTH DB를 유지하는 경우 `ALTER TABLE auth ADD COLUMN IF NOT EXISTS token_version BIGINT NOT NULL DEFAULT 1 CHECK (token_version >= 1);`을 적용한다. 개발·운영 모두 `ddl-auto: validate`이므로 컬럼 추가가 필요하다. 초기화용 `schema.sql`은 기존 데이터에 실행하지 않는다.
2. AUTH와 보호 API 서비스들을 함께 배포한다. 새 버전/종류 정보가 없는 기존 토큰은 인증에 사용할 수 없으므로 사용자는 다시 로그인해야 한다.
3. 모든 서비스의 인증 상태 저장소는 AUTH의 Refresh Token 저장소와 같은 Redis 호스트·포트·DB를 사용해야 한다. 로컬 기본값은 `localhost:6390`, DB `0`이다. 다른 주소를 사용하면 AUTH의 `spring.data.redis.*`와 다른 서비스의 `TOKEN_STATE_REDIS_HOST`, `TOKEN_STATE_REDIS_PORT`, `TOKEN_STATE_REDIS_DATABASE`, 필요 시 `TOKEN_STATE_REDIS_PASSWORD`를 맞춘다. 운영 기본값은 `refresh-redis:6379`, DB `0`이다.

좌석·대기열 서비스는 기존 Redis 연결을 유지하며 인증 상태 조회에는 별도 연결을 사용한다. 인증 상태에는 TTL을 설정하지 않는다. Redis 상태를 잃으면 해당 계정은 다시 로그인해 상태를 복구할 때까지 보호 API에 접근할 수 없다.

USER 변경 응답과 Kafka 소비 사이에는 지연이 있다. Access Token 폐기는 AUTH가 이벤트를 소비해 Redis 상태 게시를 완료한 시점부터 적용되며, 이미 인증을 통과한 요청을 소급 취소하지는 않는다.

## 검증

```powershell
.\gradlew.bat test --offline --no-daemon

# 실제 Redis 테스트에는 기존 데이터를 삭제하지 않는 별도 Redis/DB를 사용한다.
$env:TICKET_REDIS_INTEGRATION = "true"
$env:TICKET_TEST_REDIS_HOST = "127.0.0.1"
$env:TICKET_TEST_REDIS_PORT = "16390"
$env:TICKET_TEST_REDIS_DATABASE = "15"
.\gradlew.bat :auth-service:test --tests '*RefreshTokenRedisIntegrationTest' --offline --no-daemon
```
