package dev.bum.ticket_service.jpa.event.policy;

import dev.bum.common.kafka.event.EventQueuePolicyEvent;
import dev.bum.ticket_service.jpa.event.event.Event;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(
        name = "event_policy_outbox",
        indexes = {
                @Index(name = "idx_event_policy_ready", columnList = "published,next_attempt_at,outbox_id"),
                @Index(name = "idx_event_policy_event", columnList = "event_id,outbox_id")
        }
)
@Getter
@NoArgsConstructor
public class EventPolicyOutbox {

    // 발행 대기 이벤트 식별자.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long outboxId;

    // 공연 식별자.
    @Column(nullable = false, updatable = false)
    private Long eventId;

    // 공연 상태.
    @Column(nullable = false, updatable = false, length = 30)
    private String status;

    // 예매 판매 시작 시각.
    @Column(nullable = false, updatable = false)
    private long saleStartAt;

    // 예매 판매 종료 시각.
    @Column(nullable = false, updatable = false)
    private long saleEndAt;

    // 공연 시작 일시.
    @Column(nullable = false, updatable = false)
    private long eventDateTime;

    // 한 번에 입장 가능한 인원 수.
    @Column(nullable = false, updatable = false)
    private int admissionSize;

    // 발행 여부.
    @Column(nullable = false)
    private boolean published = false;

    // 재시도 횟수.
    @Column(nullable = false)
    private int retryCount;

    // 다음 재시도 예정 시각.
    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    // 생성 시각.
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 처리 시점의 데이터를 스냅샷으로 구성한다.
    public static EventPolicyOutbox snapshot(Event event, boolean deleted) {

        EventPolicyOutbox row = new EventPolicyOutbox();
        row.eventId = event.getEventId();
        row.status = deleted ? "DELETED" : event.getStatus() == null ? "INVALID" : event.getStatus().name();
        row.saleStartAt = epoch(event.getSaleStartAt());
        row.saleEndAt = epoch(event.getSaleEndAt());
        row.eventDateTime = epoch(event.getEventDateTime());
        row.admissionSize = event.getQueueAdmissionSize();
        row.createdAt = LocalDateTime.now();
        row.nextAttemptAt = row.createdAt;
        return row;
    }

    // 날짜와 시각을 에포크 기준 값으로 변환한다.
    private static long epoch(LocalDateTime time) {

        return time == null ? -1 : time.atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli();
    }

    // 처리 결과에 대응하는 안내 메시지를 반환한다.
    public EventQueuePolicyEvent message() {

        return new EventQueuePolicyEvent(eventId, outboxId, status, saleStartAt, saleEndAt, eventDateTime, admissionSize);
    }

    // 공연 처리에서 전달 완료 상태를 반영한다.
    public void delivered() {

        published = true;
    }

    // 공연 처리에서 실패 사유와 상태를 반영한다.
    public void failed() {

        retryCount++;
        nextAttemptAt = LocalDateTime.now().plusSeconds(10);
    }
}
