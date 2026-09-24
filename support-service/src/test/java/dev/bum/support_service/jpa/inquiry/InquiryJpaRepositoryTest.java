package dev.bum.support_service.jpa.inquiry;

import dev.bum.common.service.support.inquiry.enums.InquiryCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class InquiryJpaRepositoryTest {

    @Autowired
    private InquiryJpaRepository inquiryRepository;

    @Autowired
    private InquiryAnswerJpaRepository answerRepository;

    @Test
    void 문의와_하나의_관리자_답변을_저장한다() {
        Inquiry inquiry = inquiryRepository.saveAndFlush(
                Inquiry.create("user1", InquiryCategory.REFUND, "환불 문의", "환불은 언제 되나요?")
        );
        InquiryAnswer answer = answerRepository.saveAndFlush(
                InquiryAnswer.create(inquiry, "admin1", "환불 처리 중입니다.")
        );

        var selected = answerRepository.findByInquiryId(inquiry.getId()).orElseThrow();

        assertThat(selected.getId()).isEqualTo(answer.getId());
        assertThat(selected.getInquiry().getId()).isEqualTo(inquiry.getId());
        assertThat(selected.getResponderId()).isEqualTo("admin1");
    }
}
