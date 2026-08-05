package dev.bum.ticket_service.jpa.seat.cache;

public enum SeatCacheSyncFailureStatus {
    // Redis 동기화 실패 후 아직 보정되지 않은 상태
    PENDING,

    // 운영자 또는 보정 작업으로 Redis 상태 동기화가 완료된 상태
    RESOLVED,

    // 재처리하지 않기로 결정하고 보정 대상에서 제외한 상태
    DISCARDED
}
