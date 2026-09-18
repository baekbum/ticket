package dev.bum.support_service.service.faq;

import dev.bum.common.service.support.faq.dto.CreateFaqRequest;
import dev.bum.common.service.support.faq.dto.UpdateFaqRequest;
import dev.bum.common.service.support.faq.enums.FaqCategory;
import dev.bum.common.service.support.notice.enums.PublicationStatus;
import dev.bum.support_service.jpa.faq.Faq;
import dev.bum.support_service.jpa.faq.FaqRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FaqManagementServiceTest {

    @Mock
    private FaqRepository faqRepository;

    private FaqManagementService faqManagementService;

    @BeforeEach
    void setUp() {
        faqManagementService = new FaqManagementService(faqRepository);
    }

    @Test
    void 공개_FAQ를_생성한다() {
        CreateFaqRequest request = new CreateFaqRequest(
                "  예매 내역은 어디에서 확인하나요?  ",
                "  마이티켓에서 확인할 수 있습니다.  ",
                FaqCategory.BOOKING,
                2,
                true
        );
        when(faqRepository.save(any(Faq.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = faqManagementService.insert(request, " admin ");

        assertThat(response.question()).isEqualTo("예매 내역은 어디에서 확인하나요?");
        assertThat(response.answer()).isEqualTo("마이티켓에서 확인할 수 있습니다.");
        assertThat(response.category()).isEqualTo(FaqCategory.BOOKING);
        assertThat(response.status()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(response.displayOrder()).isEqualTo(2);
        assertThat(response.authorId()).isEqualTo("admin");
    }

    @Test
    void FAQ를_수정하고_숨김_처리한다() {
        Faq faq = Faq.create("기존 질문", "기존 답변", FaqCategory.ETC, 10, "admin");
        faq.publish();
        when(faqRepository.findById(1L)).thenReturn(faq);
        UpdateFaqRequest request = new UpdateFaqRequest(
                "수정 질문",
                "수정 답변",
                FaqCategory.PAYMENT,
                PublicationStatus.HIDDEN,
                1
        );

        var response = faqManagementService.update(1L, request);

        assertThat(response.question()).isEqualTo("수정 질문");
        assertThat(response.category()).isEqualTo(FaqCategory.PAYMENT);
        assertThat(response.status()).isEqualTo(PublicationStatus.HIDDEN);
        assertThat(response.displayOrder()).isOne();
    }

    @Test
    void FAQ_삭제는_보관_상태로_전환한다() {
        Faq faq = Faq.create("질문", "답변", FaqCategory.ETC, 0, "admin");
        when(faqRepository.findById(1L)).thenReturn(faq);

        faqManagementService.delete(1L);

        assertThat(faq.getStatus()).isEqualTo(PublicationStatus.ARCHIVED);
    }
}
