# Kafka DLQ 운영 절차

이 문서는 Kafka Consumer 처리 실패가 DLT로 이동했을 때 운영자가 확인하고 복구하는 절차를 정리한다.

## 적용 대상

| 원본 Topic | DLT Topic | Producer | Consumer | 재처리 대상 |
| --- | --- | --- | --- | --- |
| `user-event` | `user-event.DLT` | `user-service` | `auth-service` | 사용자 인증 DB 동기화 |
| `audit-log` | `audit-log.DLT` | `common` `AuditLogProducer` | `audit-service` | 감사 로그 저장 |
| `login-log` | `login-log.DLT` | `auth-service` | `audit-service` | 로그인 로그 저장 |
| `payment-completed` | `payment-completed.DLT` | `ticket-service` | 없음 | 결제 완료 후속 Consumer 추가 시 사용 |
| `virtual-account-expired` | `virtual-account-expired.DLT` | `payment-gateway-service` Outbox | `ticket-service` | 가상계좌 만료에 따른 결제·예매 만료 반영 |

## 발생 흐름

1. Kafka Consumer가 메시지 처리 중 예외를 던진다.
2. `DefaultErrorHandler`가 1초 간격으로 최대 3회 재시도한다.
3. 재시도 후에도 실패하면 `DeadLetterPublishingRecoverer`가 원본 topic 뒤에 `.DLT` suffix를 붙여 DLT topic으로 발행한다.
4. DLT 발행 시점에 `KafkaDltSlackNotifier`가 Slack webhook으로 알림을 전송한다.
5. `admin-service`의 전용 DLT 리스너가 원본 key/payload/header 바이트와 topic/partition/offset을 `dlq_messages`에 저장한다.
6. DB 커밋 후 리스너 그룹의 Kafka 오프셋을 커밋한다. DB 장애는 계속 재시도하며 건너뛰지 않는다. DB 커밋 후 오프셋 커밋 전에 종료되어도 원본 위치의 UNIQUE 제약으로 중복 수집을 막는다.
7. 운영자는 DB에 수집된 메시지를 조회하고 처리한다. 목록·상세·재발행은 Kafka 원본 조회를 하지 않는다.

## DB 수집 전환 및 상태

배포 전에 `admin-service/src/main/resources/sql/schema.sql`의 `dlq_messages` 테이블과 인덱스를 적용한다. 기존 이력 테이블은 유지한다. 별도 Outbox나 자동 재발행 스케줄러는 사용하지 않는다.

전용 그룹 기본값은 `admin-dlq-inbox-v1`이며 `app.kafka-dlq.inbox-group-id`로 변경할 수 있다. 처음 시작하면 Kafka에 **아직 보존된** 가장 오래된 메시지부터 수집한다. 보존 기간이 지나 이미 삭제된 원본은 복구할 수 없다. 전환 전 SUCCESS 이력이 있는 메시지는 완료 상태로 수집한다. 전환 시 이전 버전 관리 서버를 먼저 내려 구버전 재발행과 수집이 경쟁하지 않게 한다.

원본의 중복 헤더 이름, 순서, null 값도 보존한다. 재발행은 원본 업무 헤더를 전달하되 `kafka_dlt-`, `kafka_exception-` 진단 헤더는 제외한다. payload 내부 멱등키는 변경하지 않는다. 수집 그룹의 오프셋 이동은 관리자의 재처리 여부와 무관하며 Kafka 레코드를 삭제하지 않는다.

| 상태 | 의미 |
| --- | --- |
| PENDING | 수집되었고 아직 처리하지 않음 |
| REPLAYING | 처리권과 PROCESSING 시도 이력을 커밋함 |
| REPLAYED / MODIFIED_REPLAYED | 브로커 발행 성공 응답을 받고 DB에도 기록함 |
| DISCARDED | 관리자가 폐기 처리함 |
| FAILED | 직렬화 오류 등 발행 불가가 확인됨 |
| UNKNOWN | 타임아웃·통신 오류 등으로 발행 여부 확인 필요 |

재발행은 짧은 REQUIRES_NEW 트랜잭션에서 메시지 행을 잠그고 처리권/이력을 저장한 후, 트랜잭션 밖에서 브로커 응답을 기다린다. 성공 또는 실패 결과는 별도 REQUIRES_NEW 트랜잭션에서 메시지와 이력에 함께 반영한다. 동시 요청은 REPLAYING 상태를 보고 거부한다. 기본 응답 대기 시간은 10초(`app.kafka-dlq.send-timeout-ms`)이고 producer `acks=all`을 사용한다.

브로커 발행 성공은 소비자의 업무 처리 성공을 뜻하지 않는다. 발행은 성공했으나 결과 기록에 실패하거나 프로세스가 종료되면 REPLAYING으로 남는다. UNKNOWN과 FAILED는 운영자가 직접 재시도할 수 있으며 자동으로 재발행하지 않는다.

10분 이상 REPLAYING으로 남은 건은 기존 발행 작업이 종료되었는지와 원본 토픽/소비 결과를 확인한 후 화면의 `장시간 진행 건 확인`을 사용한다. API는 `POST /api/v1/manage/kafka-dlq/replay/release-stalled`이며 재발행과 같은 위치·처리자·사유를 받는다. UNKNOWN으로만 전환하며 자동 발행하지 않는다. 늦게 반환된 이전 시도 결과는 현재 시도 ID 검증으로 거부한다. 재시도 시 메시지가 중복 발행될 수 있으므로 소비자의 멱등 처리는 여전히 필요하다.

목록은 기존 응답 형식을 유지하며 `page`(0부터), `size`(최대 100), `status` 필터를 지원한다. 기본 정렬은 최신 발생 시각/ID 순이다. `fromOffset`을 지정하려면 `partition`도 지정해야 하며 해당 파티션의 오프셋 오름차순으로 조회한다.

DB에 저장한 DLT 원본은 Kafka retention과 독립적으로 남는다. 민감한 payload 접근 권한과 DB 보관/삭제 정책을 적용해야 한다.

검증용 환경변수 `DLQ_POSTGRES_PORT`를 지정하면 격리된 PostgreSQL의 `dlq_test` DB(사용자/비밀번호 `dlq_test`)에서 통합 테스트를 실행한다. 해당 DB는 테스트 중 스키마를 생성·삭제하므로 운영 DB를 사용하면 안 된다. `DLQ_KAFKA_BOOTSTRAP`도 지정하면 테스트 전용 토픽/그룹으로 실제 Kafka의 DB 저장 실패 재시도, 오프셋 커밋, DB 기반 재발행까지 검증한다.

Slack webhook URL은 각 서비스 프로세스 환경변수 `SLACK_WEBHOOK_URL`로 전달한다.
값이 없거나 `app.kafka-dlt.slack.enabled=false`이면 DLT Slack 알림은 전송하지 않는다.
관련 Grafana 대시보드 링크는 `app.kafka-dlt.slack.grafana-dashboard-url`로 지정한다.
기본값은 서비스별 Spring Boot/JVM 대시보드를 `var-application=${spring.application.name}`으로 필터링한 URL이다.

## Slack 알림 확인

Slack 메시지에서 먼저 아래 항목을 확인한다.

| 항목 | 확인 목적 |
| --- | --- |
| `Service` | 실패가 발생한 Consumer 서비스 |
| `Origin Topic` | 원본 topic |
| `DLT Topic` | 이동된 DLT topic |
| `Partition`, `Offset` | 관리자 조회/재처리 대상 위치 |
| `Key` | 멱등 처리 기준 |
| `Exception` | 일시 장애인지 payload 오류인지 판단 |
| `Payload Preview` | 잘못된 필드나 schema 불일치 여부 1차 확인 |

Slack 알림은 장애 인지용이다. 실제 처리 여부는 반드시 관리자 DLQ 화면의 상세 정보와 처리 이력으로 판단한다.

## 관리자 처리 절차

### 1. DLT 메시지 조회

관리자 화면에서 `DLQ 관리` 메뉴를 열고 Slack 알림의 `DLT Topic`, `Partition`, `Offset` 기준으로 메시지를 조회한다.

API로 조회할 경우:

```http
GET /api/v1/manage/kafka-dlq/messages?dltTopic=user-event.DLT&partition=0&fromOffset=10&size=20
GET /api/v1/manage/kafka-dlq/messages/detail?dltTopic=user-event.DLT&partition=0&offset=10
```

### 2. 처리 방향 결정

| 상황 | 처리 |
| --- | --- |
| DB 일시 장애, 네트워크 장애, 락 경합 등 일시 오류 | 원본 topic 재발행 |
| payload 필드 누락, enum 값 오류, schema 불일치 | payload 보정 후 재발행 |
| 이미 수동 반영했거나 비즈니스상 무시 가능한 메시지 | 폐기 처리 |
| 원인 판단 불가 | 재발행하지 말고 로그/DB 상태 추가 확인 |

바로 재발행하지 말고 현재 DB 상태를 먼저 확인한다. Consumer는 멱등 처리를 전제로 하지만, 중복 실행이 안전한지 topic별 기준을 확인해야 한다.

### 3. 원본 topic 재발행

payload가 정상이고 일시 장애만 복구된 경우 사용한다.

```http
POST /api/v1/manage/kafka-dlq/replay
Content-Type: application/json

{
  "dltTopic": "user-event.DLT",
  "partition": 0,
  "offset": 10,
  "operator": "admin",
  "reason": "DB 일시 장애 복구 후 재처리"
}
```

### 4. payload 보정 후 재발행

payload 오류가 명확하고 보정값이 안전한 경우에만 사용한다.

```http
POST /api/v1/manage/kafka-dlq/replay/modified
Content-Type: application/json

{
  "dltTopic": "user-event.DLT",
  "partition": 0,
  "offset": 10,
  "operator": "admin",
  "reason": "누락 필드 보정 후 재처리",
  "modifiedPayload": "{\"eventType\":\"UPDATE\",\"userId\":\"user01\"}"
}
```

보정 재발행은 원본 DLT record를 수정하지 않는다. 보정 payload와 처리 이력만 별도로 저장한다.

### 5. 폐기 처리

재발행하지 않기로 결정한 메시지는 폐기 사유를 남긴다.

```http
POST /api/v1/manage/kafka-dlq/discard
Content-Type: application/json

{
  "dltTopic": "audit-log.DLT",
  "partition": 0,
  "offset": 15,
  "operator": "admin",
  "reason": "중복 감사 로그로 확인되어 폐기"
}
```

폐기는 Kafka 메시지를 즉시 삭제하지 않는다. DLT topic의 retention 조건에 따라 Kafka에 남아 있고, 관리자 이력상 처리 완료 상태로만 기록된다.

## 처리 이력 확인

처리 후 `DLQ 처리 이력` 메뉴에서 결과를 확인한다.

API로 조회할 경우:

```http
POST /api/v1/manage/kafka-dlq/histories
GET /api/v1/manage/kafka-dlq/histories/{id}
```

확인 기준:

| 항목 | 기준 |
| --- | --- |
| `status` | `SUCCESS`면 운영 처리 완료 |
| `action` | `REPLAY`, `MODIFIED_REPLAY`, `DISCARD` 중 실제 수행한 작업 |
| `targetTopic` | 원본 topic 매핑이 맞는지 확인 |
| `operator`, `reason` | 처리자와 판단 근거가 남아 있어야 함 |
| `payloadSnapshot` | 보정 재발행이면 보정 payload 확인 |

동일한 `dltTopic + partition + offset` 조합은 이미 `SUCCESS` 처리된 경우 중복 재처리하지 않는다.

## 운영 주의사항

1. DLT 메시지는 원본 topic으로 되돌리면 Consumer가 다시 처리하므로 중복 실행 안전성을 먼저 확인한다.
2. `user-event`는 사용자 인증 DB 동기화 메시지이므로 `userId` 기준 현재 상태를 확인한 뒤 재처리한다.
3. `audit-log`는 중복 감사 로그 가능성이 있으므로 동일 `traceId`, `requestId`, `action`, `occurredAt` 조합을 확인한다.
4. `payment-completed`는 현재 Consumer가 없으므로 운영 재처리 대상은 아니며, 후속 Consumer 도입 후 활성 처리한다.
5. DLT topic은 14일 또는 partition당 1GB 보관 정책을 사용한다. 장기 판단 근거는 처리 이력 DB에 남긴다.

## 설정 체크리스트

| 서비스 | 설정 |
| --- | --- |
| `auth-service` | `SLACK_WEBHOOK_URL`, `topic.user.name` |
| `audit-service` | `SLACK_WEBHOOK_URL`, `topic.audit.log.name` |
| `ticket-service` | `SLACK_WEBHOOK_URL`, `topic.payment.completed.name` |
| `admin-service` | `app.kafka-dlq.mappings` 또는 `app.kafka-dlq.entries` |

로컬 IntelliJ 실행 시 `auth-service`, `audit-service`, `ticket-service` Run Configuration의 Environment variables에 `SLACK_WEBHOOK_URL`을 추가한다.
