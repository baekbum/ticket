package dev.bum.common.kafka.event;

/** version은 Ticket의 영속 Outbox 번호다. 삭제도 DELETED 상태의 스냅샷으로 보존한다. */
public record EventQueuePolicyEvent(
        // 공연 식별자.
        Long eventId,

        // 동시성 제어 버전.
        long version,

        // 공연 상태.
        String status,

        // 예매 판매 시작 시각.
        long saleStartAt,

        // 예매 판매 종료 시각.
        long saleEndAt,

        // 공연 시작 일시.
        long eventDateTime,

        // 한 번에 입장 가능한 인원 수.
        int admissionSize
) {}
