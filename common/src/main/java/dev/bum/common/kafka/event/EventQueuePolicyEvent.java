package dev.bum.common.kafka.event;

/** version은 Ticket의 영속 Outbox 번호다. 삭제도 DELETED 상태의 스냅샷으로 보존한다. */
public record EventQueuePolicyEvent(Long eventId, long version, String status, long saleStartAt,
                                   long saleEndAt, long eventDateTime, int admissionSize) {}
