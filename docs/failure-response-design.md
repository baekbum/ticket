# 장애 대응 설계

이 문서는 Kafka 재처리, DLQ, Redis 장애 시 동작, 서비스 간 호출 타임아웃/재시도 정책을 정리한다.
목표는 장애 상황에서도 중복 처리, 데이터 불일치, 무한 대기를 줄이고 운영자가 복구할 수 있는 기준을 고정하는 것이다.

## 적용 범위

| 영역 | 대상 |
| --- | --- |
| Kafka | `user-event`, `audit-log`, `payment-completed` |
| Redis | `auth-service` Refresh Token Redis, `ticket-service` Seat Redis, `queue-service` Queue Redis |
| HTTP 호출 | Feign 기반 서비스 간 호출, 특히 `ticket-service -> queue-service` |
| 운영 복구 | DLQ 재처리, Redis 복구, 타임아웃/재시도 기준 |

## 기본 원칙

1. 사용자 결제, 좌석 점유, 대기열 검증처럼 중복 실행이 위험한 요청은 무조건 짧은 타임아웃과 제한된 재시도만 허용한다.
2. Kafka Consumer는 처리 실패를 삼키지 않고 예외를 전파해 재처리 또는 DLQ로 이동시킨다.
3. Kafka Consumer 로직은 같은 메시지가 여러 번 처리되어도 최종 상태가 깨지지 않도록 멱등성을 확보한다.
4. Redis는 기능별 중요도가 다르므로 fail-open과 fail-closed를 명확히 나눈다.
5. DB 트랜잭션 커밋 전 외부 부수 효과를 발생시키지 않는다. 커밋 이후 실패한 부수 효과는 로그와 재처리 대상으로 남긴다.

## Kafka 장애 대응

### 현재 상태

| Topic | Producer | Consumer | 현재 재처리 상태 |
| --- | --- | --- | --- |
| `user-event` | `user-service` | `auth-service` | Consumer 내부에서 예외를 catch하고 로그만 남김 |
| `audit-log` | `common` `AuditLogProducer` | `audit-service` | Consumer 예외 발생 시 Spring Kafka 기본 동작 의존 |
| `payment-completed` | `ticket-service` | 없음 | Producer는 있으나 결제 완료 흐름에서 비활성화 |

`ticket-service`에는 `DefaultErrorHandler`와 `DeadLetterPublishingRecoverer` 설정이 존재한다.
하지만 실제 Consumer가 없는 상태이며, 다른 서비스에는 동일 정책이 적용되어 있지 않다.

### 목표 정책

Kafka Consumer 공통 정책:

| 항목 | 정책 |
| --- | --- |
| Offset commit | 처리 성공 후 commit |
| Retry | 1초 간격 최대 3회 |
| DLQ Topic | 원본 topic 뒤에 `.DLT` suffix |
| DLQ Partition | 원본 record partition 유지 |
| 실패 로그 | topic, partition, offset, key, deliveryAttempt, exception 기록 |
| Poison pill | 역직렬화 실패도 DLQ로 이동하도록 별도 recoverer 적용 |

DLQ Topic:

| 원본 Topic | DLQ Topic |
| --- | --- |
| `user-event` | `user-event.DLT` |
| `audit-log` | `audit-log.DLT` |
| `payment-completed` | `payment-completed.DLT` |

DLT topic 보관 정책:

| 항목 | 정책 |
| --- | --- |
| 보관 기간 | 14일 (`retention.ms=1209600000`) |
| 보관 용량 | partition당 1GB (`retention.bytes=1073741824`) |
| 삭제 방식 | Kafka retention 조건에 따라 오래된 segment 자동 삭제 |
| 장기 보관 | 처리 이력 DB에 운영 결과와 payload snapshot을 저장 |

DLT topic은 장애 분석과 재처리를 위해 일반 topic보다 길게 보관한다.
단, 메시지는 consumer가 읽어도 삭제되지 않으므로 무제한 보관하지 않는다.
기존에 생성된 topic은 Spring `NewTopic` 설정만으로 retention이 바뀌지 않을 수 있으므로 `kafka-configs --alter`로 운영 중 topic 설정을 별도 적용한다.

### Consumer 처리 기준

#### `user-event`

목적은 `user-service`의 사용자 정보를 `auth-service` 인증 DB에 동기화하는 것이다.

처리 규칙:

1. `CREATE`는 이미 존재하면 성공 처리한다.
2. `UPDATE`는 대상이 없으면 `CREATE`와 동일하게 upsert한다.
3. `DELETE`는 대상이 없어도 성공 처리한다.
4. 처리 중 DB 오류, 알 수 없는 event type, 필수 필드 누락은 예외를 전파한다.
5. Consumer 내부에서 예외를 catch하고 종료하지 않는다.

이 기준을 적용하면 같은 메시지가 재처리되어도 최종 사용자 인증 상태가 유지된다.

#### `audit-log`

목적은 서비스별 감사 로그를 `audit-service` DB에 저장하는 것이다.

처리 규칙:

1. `traceId`, `requestId`, `serviceName`, `action`, `targetType`, `occurredAt` 조합으로 중복 저장을 방지한다.
2. 중복 메시지는 성공 처리한다.
3. DB 일시 오류는 재시도한다.
4. 스키마 불일치, 필수 필드 누락은 DLQ로 보낸다.
5. 감사 로그 유실은 서비스 본 요청 실패로 전파하지 않는다. Producer 실패는 경고 로그로 남긴다.

#### `payment-completed`

현재는 Consumer가 없으므로 발행도 비활성화되어 있다.
알림, 정산, 배송 연동 Consumer가 생기는 시점에 다시 활성화한다.

처리 규칙:

1. 결제 완료 이벤트 key는 `paymentNo`로 고정한다.
2. Consumer는 `paymentNo` 기준으로 멱등 처리한다.
3. 후속 처리가 실패해도 결제 DB 트랜잭션은 되돌리지 않는다.
4. 후속 처리 실패는 재시도 후 DLQ로 이동시키고 운영 복구 대상으로 남긴다.

## Kafka DLQ 재처리

### 재처리 전 확인

DLQ 메시지는 바로 원본 topic으로 되돌리지 않는다. 아래 정보를 먼저 확인한다.

| 확인 항목 | 기준 |
| --- | --- |
| 예외 유형 | 일시 장애인지 데이터 오류인지 분류 |
| 원본 위치 | topic, partition, offset 확인 |
| message key | 멱등 처리 기준 확인 |
| payload | 필수 필드와 schema version 확인 |
| 현재 DB 상태 | 이미 반영된 메시지인지 확인 |

### 재처리 방식

1. 일시 장애가 원인이고 payload가 정상인 경우 원본 topic으로 재발행한다.
2. payload 보정이 필요한 경우 보정된 payload를 원본 topic으로 재발행하고, 원본 DLQ record는 별도 기록한다.
3. 비즈니스상 폐기할 메시지는 폐기 사유를 남기고 재발행하지 않는다.
4. 재처리 도구는 같은 DLQ record를 중복 재발행하지 않도록 재처리 이력을 저장한다.

현재 1차 운영 기능은 `admin-service`의 관리자 API로 제공한다.

| 기능 | API | 설명 |
| --- | --- | --- |
| DLQ 재발행 | `POST /api/v1/manage/kafka-dlq/replay` | 허용된 DLT record를 원본 topic으로 재발행 |
| DLQ 폐기 기록 | `POST /api/v1/manage/kafka-dlq/discard` | DLT record 존재를 확인하고 폐기 로그 기록 |

요청은 DLT 위치와 처리 사유를 명시한다.

```json
{
  "dltTopic": "user-event.DLT",
  "partition": 0,
  "offset": 10,
  "operator": "admin",
  "reason": "일시 DB 장애 복구"
}
```

`admin-service`는 허용된 DLT mapping만 처리한다.
현재 허용 mapping은 `user-event.DLT -> user-event`, `audit-log.DLT -> audit-log`, `payment-completed.DLT -> payment-completed`다.
재발행은 payload를 byte 단위로 읽어 원본 topic에 다시 publish한다.

재처리 이력 최소 필드:

| 필드 | 설명 |
| --- | --- |
| `sourceTopic` | DLQ topic |
| `sourcePartition` | DLQ partition |
| `sourceOffset` | DLQ offset |
| `targetTopic` | 재발행 topic |
| `messageKey` | Kafka key |
| `operator` | 처리자 |
| `result` | `REPLAYED`, `DISCARDED`, `FAILED` |
| `reason` | 처리 사유 |
| `processedAt` | 처리 시각 |

## Redis 장애 대응

Redis는 용도별로 장애 시 서비스 영향도가 다르다.

| Redis | 사용 서비스 | 용도 | 장애 정책 |
| --- | --- | --- | --- |
| Refresh Token Redis | `auth-service` | Refresh Token 저장, 재발급, 로그아웃 | fail-closed |
| Seat Redis | `ticket-service` | 좌석 캐시, 선점 락, 구매 수량 캐시 | fail-closed |
| Queue Redis | `queue-service` | 대기열, active token | fail-closed |

### Refresh Token Redis

현재 동작:

- 로그인 시 Refresh Token 저장 실패는 `RedisException`으로 실패 처리한다.
- 재발급은 Redis에 저장된 Refresh Token과 요청 토큰을 비교한다.
- 로그아웃은 Redis 토큰 조회 후 삭제한다.

목표 정책:

| 상황 | 동작 |
| --- | --- |
| 로그인 중 Redis 장애 | 로그인 실패. Access Token만 발급하지 않는다. |
| 토큰 재발급 중 Redis 장애 | 재발급 실패. 사용자는 재로그인한다. |
| 로그아웃 중 Redis 장애 | 서버 토큰 폐기에 실패했음을 반환하되, 프론트는 로컬 토큰을 삭제한다. |
| Redis 복구 후 | TTL 기준으로 기존 Refresh Token 만료를 기다린다. |

이 영역은 인증 보안과 직접 연결되므로 fail-open하지 않는다.

### Seat Redis

현재 동작:

- 좌석 선점은 Redis lock key와 seat key를 사용한다.
- Redis 좌석 캐시가 없으면 DB 상태로 선점 가능 여부를 검증한다.
- 예매, 결제, 취소 후 Redis 동기화는 DB 커밋 이후 실행한다.

목표 정책:

| 상황 | 동작 |
| --- | --- |
| 좌석 점유 중 Redis 장애 | 좌석 점유 실패. DB만으로 선점 진행하지 않는다. |
| 일부 좌석 lock 후 실패 | 획득한 lock과 seat key를 가능한 범위에서 rollback한다. |
| DB 커밋 후 Redis 동기화 실패 | DB를 진실 원천으로 유지하고 보정 작업 대상으로 남긴다. |
| Redis 캐시 유실 | 좌석 조회는 DB 기준으로 복구 가능하나, 신규 점유 전 캐시 warm-up을 수행한다. |
| 구매 수량 캐시 유실 | DB 기준 구매 제한 검증을 먼저 수행하고 Redis 카운터를 재적재한다. |

좌석 선점은 동시성 제어 핵심이므로 Redis 장애 시 fail-closed가 맞다.
단, Redis 캐시 miss는 장애로 보지 않고 DB 검증 후 캐시를 다시 채운다.

### Queue Redis

현재 동작:

- 대기열 순번, active token, 사용자별 active token 역방향 key를 Redis에 저장한다.
- token TTL은 기본 20분이다.
- `ticket-service`는 `queue-service` 검증 실패 시 좌석 점유와 결제를 막는다.

목표 정책:

| 상황 | 동작 |
| --- | --- |
| 대기열 진입 중 Redis 장애 | 진입 실패. 사용자는 잠시 후 재시도한다. |
| 상태 조회 중 Redis 장애 | 상태 조회 실패. 프론트는 짧은 간격으로 재시도한다. |
| Queue token 검증 중 장애 | `ticket-service`는 좌석 점유, 체크아웃, 결제를 차단한다. |
| Queue token 회수 실패 | 결제/예약 완료는 유지하고 경고 로그만 남긴다. TTL 만료로 자연 회수한다. |
| Redis 전체 유실 | 기존 대기 순번과 active token은 복구하지 않고 사용자를 재진입시킨다. |

대기열은 트래픽 제어 장치이므로 검증 단계는 fail-closed다.
단, 결제 커밋 이후 token 회수는 부수 효과이므로 실패해도 결제 성공을 되돌리지 않는다.

## 타임아웃 정책

서비스 간 HTTP 호출은 기본 타임아웃에 의존하지 않는다.
모든 Feign Client에 명시적인 connect/read timeout을 둔다.

| 호출 유형 | Connect timeout | Read timeout | 이유 |
| --- | --- | --- | --- |
| 대기열 검증 | 500ms | 1s | 좌석/결제 요청 경로이므로 빠르게 실패 |
| 대기열 완료 | 500ms | 1s | 실패해도 TTL 만료로 보정 가능 |
| Admin 조회 API | 1s | 3s | 운영 화면은 약간 더 긴 조회 허용 |
| Client API 단순 프록시 | 1s | 3s | 사용자 API 지연 상한 고정 |
| 외부 결제/알림 연동 예정 | 1s | 3s | 외부망 지연 고려, 무한 대기 방지 |

권장 설정:

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 1000
            readTimeout: 3000
            loggerLevel: basic
          queue-service-client:
            connectTimeout: 500
            readTimeout: 1000
```

## 재시도 정책

### HTTP 요청

| 요청 | 재시도 |
| --- | --- |
| `POST /queue/validate` | 서버 내부 자동 재시도 없음. 사용자가 원 요청을 다시 시도한다. |
| `POST /queue/complete` | 서버 내부 자동 재시도 없음. 실패 시 TTL 만료로 보정한다. |
| 관리자 조회성 GET | 최대 1회 재시도 가능 |
| 생성/수정/결제 POST | 자동 재시도 금지. 멱등키가 있는 경우에만 제한적으로 허용 |

POST 자동 재시도를 기본 금지하는 이유는 중복 결제, 중복 예약, 중복 이벤트 발행 위험 때문이다.
체크아웃 준비처럼 `idempotencyKey`가 있는 요청만 같은 key 조건에서 재시도할 수 있다.

### Kafka Producer

| Producer | 정책 |
| --- | --- |
| 감사 로그 Producer | 본 요청을 실패시키지 않고 경고 로그만 남긴다. |
| 사용자 이벤트 Producer | DB 커밋 이후 발행하거나 outbox로 전환한다. 현재 직접 발행이면 실패 로그를 운영 알림 대상으로 둔다. |
| 결제 완료 Producer | Consumer 도입 후 DB 커밋 이후 발행한다. 실패 시 outbox 또는 재발행 대상에 남긴다. |

운영 안정성을 높이려면 `user-event`, `payment-completed`는 outbox 패턴으로 전환하는 것이 최종 목표다.

## 운영 알림 기준

| 항목 | 알림 기준 |
| --- | --- |
| Kafka Consumer retry | 5분 내 retry 로그 급증 |
| Kafka DLQ 적재 | DLQ 메시지 1건 이상 |
| Redis 연결 실패 | 1분 내 Redis 예외 3회 이상 |
| Queue validate 실패 | `queue-service` 호출 실패율 5% 이상 |
| Feign timeout | 서비스별 timeout rate 5% 이상 |
| DB 커밋 후 Redis 동기화 실패 | 1건 이상 |

모니터링은 기존 Prometheus/Grafana 구성에 아래 지표를 추가해 추적한다.

| 지표 | 설명 |
| --- | --- |
| Kafka listener error count | Consumer 처리 실패 |
| Kafka DLQ publish count | DLQ 이동 건수 |
| Redis command error count | Redis 명령 실패 |
| Feign request duration | 서비스 간 호출 지연 |
| Feign timeout count | timeout 발생 수 |
| Queue token complete failure count | token 회수 실패 |

## 구현 TODO

아래 항목은 현재 코드와 목표 정책 사이의 차이다.

1. `auth-service`의 `UserEventConsumer`에서 예외 catch 후 삼키는 코드를 제거하고 공통 Kafka error handler를 적용한다.
2. `audit-service`에도 `DefaultErrorHandler`와 `.DLT` topic 설정을 추가한다.
3. `user-event.DLT`, `audit-log.DLT`, `payment-completed.DLT` topic bean을 명시한다.
4. Feign timeout 설정을 `admin-service`, `client-api-service`, `ticket-service`에 추가한다.
5. `queue-service-client`는 짧은 timeout을 별도로 적용한다.
6. Redis 장애 로그에 key prefix, eventId, userId, orderId 같은 복구 단서를 포함한다.
7. DB 커밋 후 Redis 좌석 동기화 실패를 운영 보정 대상으로 남기는 테이블 또는 Kafka 이벤트를 추가한다.
8. DLQ 재처리 도구 또는 관리자 API를 별도 운영 기능으로 만든다.

## 우선순위

1. Kafka Consumer 예외 삼킴 제거와 DLQ 적용
2. Feign timeout 명시
3. Redis 장애 시 사용자 응답 정책 정리 및 예외 메시지 통일
4. DLQ 재처리 이력 저장
5. Outbox 패턴 도입

## 좌석 배치도 실시간 상태 TODO

현재 사용자 좌석 조회는 DB 상태만 기준으로 응답한다.
따라서 다른 사용자가 Redis에서 임시 선점한 좌석도 DB가 아직 `AVAILABLE`이면 포도알로 보일 수 있다.
실제 선점 API에서는 Redis lock을 다시 검증하므로, 클릭 시 “이미 선점된 좌석입니다”가 발생할 수 있다.

차후 실시간 티켓팅 UX를 개선하려면 아래 순서로 작업한다.

1. 사용자 좌석 조회 API가 DB 상태와 Redis 임시 선점 상태를 합쳐 응답하도록 변경한다.
2. 현재 관리자 테스트용 `selectByCondWithCacheStatus` 흐름을 사용자 API에 적용 가능한 형태로 분리한다.
3. Redis 값이 `LOCKED:{userId}:{orderId}`이면 응답 상태를 `LOCKED`로 내려 포도알을 비활성화한다.
4. Redis 값이 `RESERVED`, `AVAILABLE` 같은 `SeatStatus` 값이면 DB 응답 상태를 Redis 상태로 보정한다.
5. Redis key가 없으면 DB 상태를 그대로 사용하되, 필요하면 좌석 캐시 warm-up 대상에 포함한다.
6. Redis 장애 시 좌석 조회 정책을 결정한다.
   - 보수적 정책: 좌석 조회 실패 또는 “잠시 후 다시 시도” 응답
   - 완화 정책: DB 기준 조회는 허용하되 선점 API에서 Redis 검증은 반드시 수행
7. 좌석 선점 성공/취소/결제 완료/예약 취소 후 프론트 갱신 방식을 정한다.
   - 1차: 짧은 주기 polling
   - 2차: SSE 또는 WebSocket 좌석 상태 이벤트
8. 프론트는 선점 실패 응답을 받으면 해당 좌석을 즉시 비활성화하고 좌석 목록을 재조회한다.
9. DB 커밋 후 Redis 동기화 실패 이력(`seat_cache_sync_failures`)을 주기적으로 재처리하는 보정 job을 추가한다.
10. 보정 job은 DB 상태를 진실 원천으로 삼아 Redis seat key와 lock key를 다시 맞춘다.

최종 목표 상태:

```text
좌석 배치도 표시 = DB 확정 상태 + Redis 임시 선점 상태
좌석 선점 확정 = Redis lock 성공 + DB 상태 검증
장애 복구 기준 = DB 상태
실시간 UX 개선 = polling 또는 SSE/WebSocket
```
