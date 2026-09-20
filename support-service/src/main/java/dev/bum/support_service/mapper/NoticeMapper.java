package dev.bum.support_service.mapper;

import dev.bum.common.service.support.notice.dto.NoticeResponse;
import dev.bum.support_service.jpa.notice.Notice;

public final class NoticeMapper {

    private NoticeMapper() {
    }

    public static NoticeResponse toResponse(Notice notice) {
        return new NoticeResponse(
                notice.getId(), notice.getTitle(), notice.getContent(), notice.getCategory(), notice.getStatus(),
                notice.getAuthorId(), notice.isPinned(), notice.getViewCount(), notice.getPublishedAt(),
                notice.getCreatedAt(), notice.getUpdatedAt()
        );
    }
}
