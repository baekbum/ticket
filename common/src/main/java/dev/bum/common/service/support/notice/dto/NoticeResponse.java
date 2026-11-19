package dev.bum.common.service.support.notice.dto;

import dev.bum.common.service.support.notice.enums.NoticeCategory;
import dev.bum.common.service.support.notice.enums.PublicationStatus;

import java.time.LocalDateTime;

public record NoticeResponse(
        // 공지사항 식별자.
        Long noticeId,

        // 제목.
        String title,

        // 본문.
        String content,

        // 분류.
        NoticeCategory category,

        // 공지사항 상태.
        PublicationStatus status,

        // 작성자 ID.
        String authorId,

        // 상단 고정 여부.
        boolean pinned,

        // 조회 수.
        long viewCount,

        // 발행 시각.
        LocalDateTime publishedAt,

        // 생성 시각.
        LocalDateTime createdAt,

        // 마지막 수정 시각.
        LocalDateTime updatedAt
) {
}
