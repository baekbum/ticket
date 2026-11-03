# 이벤트별 동시 예매 허용 인원

API 필드명은 `queueAdmissionSize`, DB 컬럼명은 `queue_admission_size`다.
신규 이벤트 및 회차의 기본값은 1000명이며 1 이상의 정수만 허용한다.
수정 요청에서 필드를 생략하거나 `null`을 보내면 기존 값을 유지한다.
조회 응답에는 저장된 값을 반환하고, 관리자 목록·상세·등록·수정에서 표시한다.
다회차 등록은 `schedules`의 각 항목에 개별 값을 지정한다.

```json
{
  "schedules": [
    { "queueAdmissionSize": 1000 },
    { "queueAdmissionSize": 250 }
  ]
}
```

위 예시는 인원 필드만 보여준다. 실제 등록에는 기존 필수 공연 정보와 일정도 필요하다.
등록·수정 시 기존 Outbox/Kafka 경로로 Queue의 이벤트별 정책을 갱신한다.
전역 `app.queue.admission-size` 값은 이벤트에 저장된 값을 덮어쓰지 않는다.

## 로컬 DB 적용 및 검증 기록

2026-11-03 `local-postgres` 컨테이너의 `ticket` DB에 적용하고 검증했다.

- 이벤트 25건의 기존 고정값 100명을 1000명으로 변경했다.
- 컬럼 기본값은 1000이며 NOT NULL 및 양수 CHECK 제약을 확인했다.
- 이벤트 ID와 인원 외 모든 컬럼은 백업과 일치한다.
- 구역 130건, 좌석 1000건과 events를 참조하는 외래 키 2개를 보존했다.
- 이벤트 ID 시퀀스의 이전 위치 27을 보존했다.
- 각 이벤트의 최신 정책 스냅샷 인원이 DB 값과 일치한다.
- 전체 DB 덤프는 `ticket-service/backups/ticket-before-admission-size-20261103-232431.dump`에 보관했다.
- 원본 이벤트 25건은 `public.events_backup_admission_size`에도 남겨뒀다.

검증이 끝난 일회성 테이블 재생성 SQL은 삭제했다. 신규 DB 테이블 정의는
`schema.sql`을 사용한다. 서비스 시작 후 기존 정책 발행 작업자가 새 스냅샷을
Kafka/Redis에 반영한다.
