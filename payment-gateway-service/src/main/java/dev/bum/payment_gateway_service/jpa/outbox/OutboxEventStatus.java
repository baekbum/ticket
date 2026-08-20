package dev.bum.payment_gateway_service.jpa.outbox;

public enum OutboxEventStatus {

    // 이벤트 발행 대기 상태.
    PENDING,

    // Kafka 이벤트 발행이 완료된 상태.
    PUBLISHED
}
