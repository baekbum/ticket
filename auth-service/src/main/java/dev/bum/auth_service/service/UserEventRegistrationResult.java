package dev.bum.auth_service.service;

public enum UserEventRegistrationResult {
    // 신규 이벤트 등록 결과.
    REGISTERED,

    // 이미 처리한 중복 요청 결과.
    DUPLICATE,

    // 이미 반영된 버전보다 오래된 이벤트 결과.
    STALE
}