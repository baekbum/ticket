package dev.bum.support_service.jpa.faq;

import dev.bum.common.service.support.faq.enums.FaqCategory;
import dev.bum.support_service.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({QuerydslConfig.class, FaqRepositoryImpl.class})
class FaqJpaRepositoryTest {

    @Autowired
    private FaqJpaRepository faqJpaRepository;

    @Autowired
    private FaqRepository faqRepository;

    @Test
    void 공개_FAQ만_노출_순서대로_조회한다() {
        Faq second = Faq.create("두 번째 질문", "답변", FaqCategory.BOOKING, 20, "admin");
        second.publish();
        Faq first = Faq.create("첫 번째 질문", "답변", FaqCategory.BOOKING, 10, "admin");
        first.publish();
        Faq draft = Faq.create("초안 질문", "작성 중", FaqCategory.BOOKING, 0, "admin");

        faqJpaRepository.save(second);
        faqJpaRepository.save(first);
        faqJpaRepository.save(draft);
        faqJpaRepository.flush();

        var result = faqRepository.findPublished(
                FaqCategory.BOOKING,
                null,
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent())
                .extracting(Faq::getQuestion)
                .containsExactly("첫 번째 질문", "두 번째 질문");
    }

    @Test
    void 질문과_답변에서_키워드를_검색한다() {
        Faq questionMatch = Faq.create("좌석 선택 시간", "10분입니다.", FaqCategory.BOOKING, 1, "admin");
        questionMatch.publish();
        Faq answerMatch = Faq.create("결제 기한", "좌석 선택 후 결제하세요.", FaqCategory.PAYMENT, 2, "admin");
        answerMatch.publish();
        faqJpaRepository.save(questionMatch);
        faqJpaRepository.save(answerMatch);
        faqJpaRepository.flush();

        var result = faqRepository.findPublished(
                null,
                "좌석",
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent()).hasSize(2);
    }
}
