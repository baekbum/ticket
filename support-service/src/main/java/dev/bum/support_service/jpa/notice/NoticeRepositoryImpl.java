package dev.bum.support_service.jpa.notice;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.bum.common.service.support.notice.dto.NoticeSearchRequest;
import dev.bum.common.service.support.notice.enums.NoticeCategory;
import dev.bum.common.service.support.notice.enums.PublicationStatus;
import dev.bum.support_service.exception.NoticeNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class NoticeRepositoryImpl implements NoticeRepository {

    private final JPAQueryFactory queryFactory;
    private final NoticeJpaRepository noticeJpaRepository;

    @Override
    public Notice save(Notice notice) {
        return noticeJpaRepository.save(notice);
    }

    @Override
    public Notice findById(Long noticeId) {
        return noticeJpaRepository.findById(noticeId)
                .orElseThrow(() -> new NoticeNotFoundException("존재하지 않는 공지사항입니다."));
    }

    @Override
    public Notice findPublishedById(Long noticeId) {
        QNotice notice = QNotice.notice;
        Notice selectedNotice = queryFactory
                .selectFrom(notice)
                .where(
                        notice.id.eq(noticeId),
                        notice.status.eq(PublicationStatus.PUBLISHED)
                )
                .fetchOne();

        if (selectedNotice == null) {
            throw publishedNoticeNotFound();
        }
        return selectedNotice;
    }

    @Override
    public Page<Notice> findPublished(
            NoticeCategory category,
            String keyword,
            Pageable pageable
    ) {
        QNotice notice = QNotice.notice;
        BooleanExpression[] conditions = {
                notice.status.eq(PublicationStatus.PUBLISHED),
                categoryEquals(notice, category),
                keywordContains(notice, keyword)
        };

        List<Notice> content = queryFactory
                .selectFrom(notice)
                .where(conditions)
                .orderBy(
                        notice.pinned.desc(),
                        notice.publishedAt.desc(),
                        notice.id.desc()
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return new PageImpl<>(content, pageable, count(conditions));
    }

    @Override
    public Page<Notice> findForManagement(NoticeSearchRequest request, Pageable pageable) {
        QNotice notice = QNotice.notice;
        BooleanExpression[] conditions = {
                statusEquals(notice, request.getStatus()),
                categoryEquals(notice, request.getCategory()),
                keywordContains(notice, request.getKeyword())
        };

        List<Notice> content = queryFactory
                .selectFrom(notice)
                .where(conditions)
                .orderBy(notice.createdAt.desc(), notice.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return new PageImpl<>(content, pageable, count(conditions));
    }

    @Override
    public void increaseViewCount(Long noticeId) {
        int updatedRows = noticeJpaRepository.increaseViewCount(noticeId, PublicationStatus.PUBLISHED);
        if (updatedRows == 0) {
            throw publishedNoticeNotFound();
        }
    }

    private long count(BooleanExpression... conditions) {
        QNotice notice = QNotice.notice;
        Long count = queryFactory
                .select(notice.count())
                .from(notice)
                .where(conditions)
                .fetchOne();
        return count == null ? 0 : count;
    }

    private BooleanExpression statusEquals(QNotice notice, PublicationStatus status) {
        return status == null ? null : notice.status.eq(status);
    }

    private BooleanExpression categoryEquals(QNotice notice, NoticeCategory category) {
        return category == null ? null : notice.category.eq(category);
    }

    private BooleanExpression keywordContains(QNotice notice, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String normalizedKeyword = keyword.trim();
        return notice.title.containsIgnoreCase(normalizedKeyword)
                .or(notice.content.containsIgnoreCase(normalizedKeyword));
    }

    private NoticeNotFoundException publishedNoticeNotFound() {
        return new NoticeNotFoundException("존재하지 않거나 공개되지 않은 공지사항입니다.");
    }
}
