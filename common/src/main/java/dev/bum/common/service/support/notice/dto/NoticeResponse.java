package dev.bum.common.service.support.notice.dto;

import dev.bum.common.service.support.notice.enums.NoticeCategory;
import dev.bum.common.service.support.notice.enums.PublicationStatus;

import java.time.LocalDateTime;

public record NoticeResponse(
        Long noticeId,
        String title,
        String content,
        NoticeCategory category,
        PublicationStatus status,
        String authorId,
        boolean pinned,
        long viewCount,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
