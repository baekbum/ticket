package dev.bum.support_service.jpa.notice;

import dev.bum.support_service.dto.notice.NoticeSearchRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NoticeRepository {

    Notice save(Notice notice);

    Notice findById(Long noticeId);

    Notice findPublishedById(Long noticeId);

    Page<Notice> findPublished(
            NoticeCategory category,
            String keyword,
            Pageable pageable
    );

    Page<Notice> findForManagement(
            NoticeSearchRequest request,
            Pageable pageable
    );

    void increaseViewCount(Long noticeId);
}
