# 티켓팅 이상 활동 탐지

`ticket-service`는 실제 예매 요청을 검증하고, 탐지 조건에 해당하는 이벤트를 Kafka로 발행한다. 세션별 집계는 Redis에서 처리하고, 관리자 조회에 사용하는 이력은 `audit-service`가 PostgreSQL에 저장한다.

## 탐지 조건

| 탐지 유형 | 기록하는 조건 | 요청 처리 | 로그 근거 |
| --- | --- | --- | --- |
| `AREA_LAYOUT_ACTIVE_TOKEN_MISSING` | 로그인한 사용자가 구역 배치도를 요청했으나 Active Token 헤더가 없거나 비어 있어 대기열 검증에서 거절된 경우 | `403 QUEUE_ACCESS_DENIED` | `{}` |
| `AREA_LAYOUT_REFRESH_LIMIT_REACHED` | 유효한 Active Token으로 같은 60초 집계 구간에 배치도를 60회 새로고침한 경우 | 배치도 요청을 정상 처리 | `refreshCount`, `windowMs` |
| `SEAT_OCCUPATION_CAPTCHA_NOT_PASSED` | 캡차 사용 회차에서 유효한 Active Token으로 좌석 선점을 요청했으나 해당 세션의 캡차 통과 기록이 없는 경우 | `403 CAPTCHA_REQUIRED` | `{}` |
| `PREPARE_TOO_FAST` | Active Token 발급부터 검증을 통과한 첫 `prepare` 요청 접수까지, **캡차 사용 회차는 5초 이내**, **캡차 미사용 회차는 3초 이내**인 경우. 두 경우 모두 좌석 선점 성공 시각이 필요하며, 캡차 사용 회차는 선점 전에 캡차를 통과해야 한다. | 결제 준비 요청을 정상 처리 | `elapsedMs`, `limitMs` |
| `CAPTCHA_ATTEMPT_LIMIT_REACHED` | 같은 Active Token에서 유효한 캡차 문제에 제출한 오답이 누적 5회에 도달한 경우 | 5번째 오답부터 `429 CAPTCHA_ATTEMPT_LIMIT_REACHED` | `attempts` |

배치도 새로고침 구간은 Active Token 발급 시각을 기준으로 나눈다. 첫 배치도 조회는 집계의 시작으로 기록하고, 그다음 조회부터 새로고침 횟수에 포함한다. 한 세션에서 한도에 처음 도달할 때 탐지 이벤트를 발행한다.

`PREPARE_TOO_FAST`는 결제 준비 **요청 접수**까지의 시간을 밀리초로 비교한다. 캡차 사용 회차는 5,000ms, 미사용 회차는 3,000ms를 포함한다. 새로고침 한도 도달과 빠른 결제 준비는 관리자가 검토할 수 있도록 기록하는 신호다.

## 저장 매체와 데이터

### Redis · 검증과 세션별 집계

회차 ID, 사용자 ID, Active Token을 묶어 계산한 SHA-256 해시를 세션 식별자로 사용한다.

| 키 | 자료형 | 저장하는 값 | 보관 기준 |
| --- | --- | --- | --- |
| `ticket:seat-activity:v1:<세션 해시>` | Hash | `areaLayoutRefreshCount`, `areaLayoutQueryWindow`, `activeTokenIssuedAt`, `captchaPassedAt`, `firstSeatOccupiedAt`, `firstPrepareRequestedAt`, `firstPrepareElapsedMs` 등 회차별 세션 활동 | Active Token 만료 시각 |
| `ticket:captcha:issued:v1:<세션 해시>:attempts` | String | 캡차 누적 오답 횟수 | Active Token 만료 시각. 캡차 통과 시 삭제 |
| `ticket:captcha:issued:v1:<세션 해시>:passed` | String | 통과한 캡차의 `challengeId`. 좌석 선점과 결제 준비에서 캡차 통과 여부를 확인할 때 사용 | Active Token 만료 시각. 예매 세션 종료 처리 시 삭제 |

캡차 이미지를 재발급해도 같은 Active Token의 오답 횟수는 유지한다. 좌석 선점 시도 횟수인 `seatOccupyAttempts`도 Redis에 집계한다. 속도 판정에는 실제 선점 성공 시각인 `firstSeatOccupiedAt`을 사용한다.

### Kafka · 탐지 이벤트 전달

`ticket-service`는 기본 토픽 `ticketing-detection-log`에 탐지 이벤트를 비동기로 발행하고, `audit-service`가 이를 소비한다. 메시지 키는 `sessionKeyHash`다. 본문에는 회차 ID, 사용자 ID, 세션 해시, 탐지 유형, 발생 시각, 유형별 근거와 수집 가능한 `traceId`를 담는다.

재시도 가능한 전송 오류에는 최대 3회 추가로 시도한다. 최종 전송 실패는 `ticket-service`의 애플리케이션 로그에 경고로 남긴다.

### PostgreSQL · 관리자 조회 이력

`audit-service`는 이벤트를 `ticketing_detection_log` 테이블에 저장한다. 탐지 근거는 `evidence` JSONB 컬럼에, 이벤트 발생 시각과 DB 저장 시각은 각각 `occurred_at`, `created_at`에 기록한다. 세션 해시는 `session_key_hash`에, 요청 추적 ID는 `trace_id`에 저장한다.

`(event_id, user_id, session_key_hash, detection_type)` 조합을 고유하게 관리한다. 동일 조합을 다시 수신하면 최초로 저장한 행을 유지한다. Active Token이 없는 배치도 요청은 토큰이 없는 상태로 해시를 계산하므로, 같은 회차와 사용자에 대해서는 해당 유형의 최초 기록이 남는다.

## 관리자 조회와 테스트

관리자 화면의 **감사/운영 → 티켓팅 이상 활동**에서 회차 ID, 사용자 ID, 탐지 유형, 발생 기간으로 조회한다. 상세 화면에는 탐지 근거, 세션 해시, `traceId`를 표시한다.

로컬 테스트 메뉴에서 발행한 샘플 이벤트는 `event_id = 0`, `evidence.test = true`로 식별한다. 샘플로 Kafka 발행부터 DB 저장과 관리자 화면 조회까지 확인할 수 있다.

## 관련 코드

- [좌석 활동 집계와 `prepare` 탐지](../ticket-service/src/main/java/dev/bum/ticket_service/service/seat/SeatActivityTracker.java)
- [배치도 요청의 대기열 검증](../ticket-service/src/main/java/dev/bum/ticket_service/controller/area/AreaController.java)
- [캡차 검증과 오답 집계](../ticket-service/src/main/java/dev/bum/ticket_service/service/captcha/CaptchaChallengeService.java)
- [감사 DB 테이블 정의](../audit-service/src/main/resources/sql/schema.sql)
