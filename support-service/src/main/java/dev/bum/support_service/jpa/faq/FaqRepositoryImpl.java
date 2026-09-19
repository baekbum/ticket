package dev.bum.support_service.jpa.faq;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.bum.common.service.support.faq.dto.FaqSearchRequest;
import dev.bum.common.service.support.faq.enums.FaqCategory;
import dev.bum.common.service.support.notice.enums.PublicationStatus;
import dev.bum.support_service.exception.FaqNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class FaqRepositoryImpl implements FaqRepository {

    private final JPAQueryFactory queryFactory;
    private final FaqJpaRepository faqJpaRepository;

    @Override
    public Faq save(Faq faq) {
        return faqJpaRepository.save(faq);
    }

    @Override
    public Faq findById(Long faqId) {
        return faqJpaRepository.findById(faqId)
                .orElseThrow(() -> new FaqNotFoundException("존재하지 않는 FAQ입니다."));
    }

    @Override
    public Page<Faq> findPublished(FaqCategory category, String keyword, Pageable pageable) {
        QFaq faq = QFaq.faq;
        BooleanExpression[] conditions = {
                faq.status.eq(PublicationStatus.PUBLISHED),
                categoryEquals(faq, category),
                keywordContains(faq, keyword)
        };

        List<Faq> content = queryFactory
                .selectFrom(faq)
                .where(conditions)
                .orderBy(faq.displayOrder.asc(), faq.id.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return new PageImpl<>(content, pageable, count(conditions));
    }

    @Override
    public Page<Faq> findForManagement(FaqSearchRequest request, Pageable pageable) {
        QFaq faq = QFaq.faq;
        BooleanExpression[] conditions = {
                statusEquals(faq, request.getStatus()),
                categoryEquals(faq, request.getCategory()),
                keywordContains(faq, request.getKeyword())
        };

        List<Faq> content = queryFactory
                .selectFrom(faq)
                .where(conditions)
                .orderBy(faq.updatedAt.desc(), faq.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return new PageImpl<>(content, pageable, count(conditions));
    }

    private long count(BooleanExpression... conditions) {
        QFaq faq = QFaq.faq;
        Long count = queryFactory
                .select(faq.count())
                .from(faq)
                .where(conditions)
                .fetchOne();
        return count == null ? 0 : count;
    }

    private BooleanExpression statusEquals(QFaq faq, PublicationStatus status) {
        return status == null ? null : faq.status.eq(status);
    }

    private BooleanExpression categoryEquals(QFaq faq, FaqCategory category) {
        return category == null ? null : faq.category.eq(category);
    }

    private BooleanExpression keywordContains(QFaq faq, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String normalizedKeyword = keyword.trim();
        return faq.question.containsIgnoreCase(normalizedKeyword)
                .or(faq.answer.containsIgnoreCase(normalizedKeyword));
    }
}
