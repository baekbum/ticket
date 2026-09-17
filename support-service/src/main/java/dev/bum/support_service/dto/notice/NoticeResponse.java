package dev.bum.support_service.dto.notice;

import dev.bum.support_service.jpa.common.PublicationStatus;
import dev.bum.support_service.jpa.notice.Notice;
import dev.bum.support_service.jpa.notice.NoticeCategory;

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
    public static NoticeResponse from(Notice notice) {
        return new NoticeResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.getCategory(),
                notice.getStatus(),
                notice.getAuthorId(),
                notice.isPinned(),
                notice.getViewCount(),
                notice.getPublishedAt(),
                notice.getCreatedAt(),
                notice.getUpdatedAt()
        );
    }
}
