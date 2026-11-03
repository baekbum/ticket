# 공연 판매 기간과 대기열 정책

## 예매 가능 조건

판매 시작·종료와 공연 일시는 관리자가 설정한 값을 사용한다. 공연일로부터 판매 종료일을 임의로 계산하지 않는다.
시각은 Asia/Seoul 기준이며 Queue에 전달할 때 epoch 밀리초로 변환한다.

- `saleStartAt <= now <= saleEndAt`, 그리고 공연 시작 전이어야 한다.
- `ON_SALE`은 신규 선점과 기존 선점의 준비·확정을 허용한다.
- `SOLD_OUT`은 신규 진입·선점을 차단한다. 유효한 기존 활성 토큰과 좌석 선점이 있는 사용자는 판매 기간 안에서 준비·확정할 수 있다.
- `CANCELLED`, `SALE_ENDED`, `CLOSED`는 차단한다.
- 판매 기간이 없거나 올바르지 않으면 허용하지 않는다.

공연이 10/31 18:00, 판매 종료가 10/30 23:59:59이면 그 이후에는 대기열을 통과했거나 좌석을 선점했더라도
`prepare`와 `confirm`은 거절한다. 동일한 `confirm`의 재요청과 결제수단 전환도 검사한다.
이미 생성된 결제는 기존 조회 API로 조회할 수 있다.
취소 마감 및 무통장 입금 기한 정책은 변경하지 않는다.

## Ticket의 최종 검증

`EventBookingGuard`는 공연에 공유 읽기 잠금을 획득하고 최신 DB 값으로 검증한다.
선점·준비·확정은 원본 공연 정보를 사용하며 Queue의 사본을 최종 판단 근거로 삼지 않는다.
준비·확정에서는 Redis 선점 확인 후에도 시각을 다시 확인한다.
관리자 수정·삭제 및 상태 정리는 같은 공연에 쓰기 잠금을 사용한다.
공유 읽기 잠금은 서로 다른 예약의 동시 진행을 허용하고 공연 상태 변경과의 경합을 제어한다.

## Queue의 Redis Hash

`queue:event:{eventId}:policy`에 다음 필드를 저장한다.

| 필드 | 내용 |
|---|---|
| `status` | 공연 상태 또는 삭제를 나타내는 `DELETED` |
| `saleStartAt`, `saleEndAt` | 판매 시작·종료 epoch 밀리초 |
| `eventDateTime` | 공연 시작 epoch 밀리초 |
| `admissionSize` | 이 공연의 동시 활성 인원 상한 |
| `version` | Ticket의 영속 Outbox 번호 |

진입과 승격 Lua에서 Redis 서버 시각으로 기간·상태를 검사하고, 승격은 같은 Lua에서 인원 계산과 토큰 발급까지 수행한다.
이미 발급된 활성 토큰을 재조회해도 판매 종료를 우회할 수 없다.
활성 토큰 검증 API도 정책을 확인한다. 퇴장·완료·만료 정리는 공연 종료 후에도 허용한다.
판매 시작 전의 별도 사전 대기 시간은 현재 두지 않는다.
정책이 없으면 503, 판매 기간·상태가 허용하지 않으면 409를 반환한다.

공연별 `queueAdmissionSize`는 등록·수정 API와 관리자 화면에서 설정하며 기본값은 1000명이다.
상한을 줄여도 기존 활성 사용자를 제거하지 않고, 인원이 새 상한 아래로 내려갈 때까지 신규 승격을 멈춘다.
예상 입장 시각 계산도 공연별 상한을 사용한다. 기존 전역 `app.queue.admission-size`로 공연별 값을 덮어쓰지 않는다.

## 정책 전달과 복구

공연 등록·수정·삭제와 `event_policy_outbox` 스냅샷 저장은 같은 DB 트랜잭션이다.
관리자 요청은 `EventManagementService`가 공연 Repository와 Outbox 서비스를 호출해 처리한다.
공연 Repository는 공연 데이터 접근만 담당하고, 스케줄러의 상태 변경은 `EventPolicyMaintenance`가 Outbox 저장까지 처리한다.
삭제 스냅샷은 공연 FK 없이 유지하여 오래된 메시지가 삭제된 공연을 다시 열지 못하게 한다.
동일 공연의 쓰기를 잠금으로 직렬화하여 증가하는 Outbox 번호를 버전으로 사용한다.

Ticket의 발행 작업자는 Kafka `event-queue-policy` 토픽에 공연 ID를 키로 전송하고 브로커 응답 후 발행 완료를 기록한다.
실패·응답 유실·완료 기록 실패는 재시도한다. Queue는 Redis 반영 성공을 확인한 뒤 레코드 처리를 완료한다.
Kafka 소비 스레드의 대기는 Netty 요청 이벤트 루프에서 실행하지 않는다.

Redis는 Lua에서 최신 버전보다 오래되거나 같은 버전의 메시지를 무시한다.
정책 키가 유실되면 같은 스냅샷을 다시 적용할 수 있다.
초기 적재·복구 작업은 기본 30초마다 최대 100개씩 기존 공연과 최신 스냅샷을 순회하고 재발행한다.
행 수가 많은 경우 모든 공연을 복구하는 데 여러 주기가 걸린다. 복구 전에는 Queue가 입장을 허용하지 않는다.
원본 변경과 Queue 반영 사이의 지연 중에도 Ticket의 원본 검증이 예약 생성을 차단한다.

최신 Outbox 행과 삭제 스냅샷은 복구 근거이므로 무조건 삭제하지 않는다.
원본 DB를 초기화하여 버전 시퀀스를 재설정할 때는 Queue 정책 사본도 함께 재구축해야 한다.

## 설정과 스키마

- Ticket: 기존 Kafka 접속 설정, `topic.event-policy.name`(기본 `event-queue-policy`).
- Queue: `SPRING_KAFKA_BOOTSTRAP_SERVERS` 또는 운영 `KAFKA_SERVER`, `topic.event-policy.name`, `topic.event-policy.group-id`.
- 기본 발행 주기: `app.event-policy.publish-delay-ms=1000`.
- 초기 적재·복구 주기: `app.event-policy.reconcile-delay-ms=30000`.
- 테이블 정의: `ticket-service/src/main/resources/sql/schema.sql`의 `events.queue_admission_size`, `event_policy_outbox`.

기존 DB를 삭제할 필요는 없다. 새 컬럼과 Outbox 테이블을 추가한 뒤 서비스를 시작하면 기존 공연 정책을 순차적으로 적재한다.
로컬은 기존 `ddl-auto: update` 설정으로 추가 스키마가 반영된다. 운영은 배포 전에 추가 DDL을 적용한다.

이벤트별 동시 예매 인원 설정과 로컬 DB 적용 결과는 [설정 및 DB 적용 기록](../ticket-service/src/main/resources/sql/README-admission-size.md)을 참고한다.
