package dev.bum.queue_service.service;

public enum QueueSessionType {
    // 공연 대기열에서 입장을 기다리는 세션 유형.
    WAITING,
    // 공연에 입장할 수 있는 세션 유형.
    ACTIVE
}
