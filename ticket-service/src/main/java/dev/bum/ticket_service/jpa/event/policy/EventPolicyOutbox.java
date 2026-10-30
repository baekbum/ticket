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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long outboxId;

    @Column(nullable = false, updatable = false)
    private Long eventId;

    @Column(nullable = false, updatable = false, length = 30)
    private String status;

    @Column(nullable = false, updatable = false)
    private long saleStartAt;

    @Column(nullable = false, updatable = false)
    private long saleEndAt;

    @Column(nullable = false, updatable = false)
    private long eventDateTime;

    @Column(nullable = false, updatable = false)
    private int admissionSize;

    @Column(nullable = false)
    private boolean published = false;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

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

    private static long epoch(LocalDateTime time) {
        return time == null ? -1 : time.atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli();
    }

    public EventQueuePolicyEvent message() {
        return new EventQueuePolicyEvent(eventId, outboxId, status, saleStartAt, saleEndAt, eventDateTime, admissionSize);
    }

    public void delivered() {
        published = true;
    }

    public void failed() {
        retryCount++;
        nextAttemptAt = LocalDateTime.now().plusSeconds(10);
    }
}
