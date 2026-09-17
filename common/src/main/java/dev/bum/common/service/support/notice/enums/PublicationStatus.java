package dev.bum.common.service.support.notice.enums;

public enum PublicationStatus {
    // 관리자가 작성 중인 초안 상태.
    DRAFT,

    // 사용자에게 공개된 게시 상태.
    PUBLISHED,

    // 삭제하지 않고 사용자 화면에서만 숨긴 상태.
    HIDDEN,

    // 운영 이력 보존을 위해 보관한 상태.
    ARCHIVED
}
