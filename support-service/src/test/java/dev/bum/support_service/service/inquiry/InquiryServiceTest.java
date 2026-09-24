package dev.bum.support_service.service.inquiry;

import dev.bum.common.service.support.inquiry.dto.CreateInquiryRequest;
import dev.bum.common.service.support.inquiry.dto.UpdateInquiryRequest;
import dev.bum.common.service.support.inquiry.enums.InquiryCategory;
import dev.bum.common.service.support.inquiry.enums.InquiryStatus;
import dev.bum.support_service.exception.InquiryNotFoundException;
import dev.bum.support_service.exception.InquiryStateConflictException;
import dev.bum.support_service.jpa.inquiry.Inquiry;
import dev.bum.support_service.jpa.inquiry.InquiryAnswerJpaRepository;
import dev.bum.support_service.jpa.inquiry.InquiryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InquiryServiceTest {

    @Mock
    private InquiryJpaRepository inquiryRepository;

    @Mock
    private InquiryAnswerJpaRepository answerRepository;

    private InquiryService inquiryService;

    @BeforeEach
    void setUp() {
        inquiryService = new InquiryService(inquiryRepository, answerRepository);
    }

    @Test
    void 문의를_답변_대기_상태로_등록한다() {
        when(inquiryRepository.save(any(Inquiry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = inquiryService.insert(
                new CreateInquiryRequest(InquiryCategory.PAYMENT, "  결제 문의  ", "  결제가 실패합니다.  "),
                " user1 "
        );

        assertThat(response.requesterId()).isEqualTo("user1");
        assertThat(response.title()).isEqualTo("결제 문의");
        assertThat(response.content()).isEqualTo("결제가 실패합니다.");
        assertThat(response.status()).isEqualTo(InquiryStatus.WAITING);
        assertThat(response.answer()).isNull();
    }

    @Test
    void 답변이_완료된_문의는_수정할_수_없다() {
        Inquiry inquiry = Inquiry.create("user1", InquiryCategory.BOOKING, "예매 문의", "문의 내용");
        inquiry.markAnswered();
        when(inquiryRepository.findByIdAndRequesterId(1L, "user1"))
                .thenReturn(Optional.of(inquiry));

        assertThatThrownBy(() -> inquiryService.update(
                1L,
                new UpdateInquiryRequest(InquiryCategory.REFUND, "환불 문의", "수정 내용"),
                "user1"
        ))
                .isInstanceOf(InquiryStateConflictException.class)
                .hasMessage("답변이 완료된 문의는 수정할 수 없습니다.");
    }

    @Test
    void 다른_사용자의_문의는_조회할_수_없다() {
        when(inquiryRepository.findByIdAndRequesterId(1L, "user2"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> inquiryService.selectById(1L, "user2"))
                .isInstanceOf(InquiryNotFoundException.class);
    }
}
