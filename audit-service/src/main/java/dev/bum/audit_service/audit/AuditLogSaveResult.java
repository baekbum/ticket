package dev.bum.audit_service.audit;

public enum AuditLogSaveResult {
    // 저장 완료 결과.
    SAVED,

    // 이미 처리한 중복 요청 결과.
    DUPLICATE
}
