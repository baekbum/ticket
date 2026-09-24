package dev.bum.support_service.service.inquiry;

import dev.bum.common.service.support.inquiry.dto.InquiryAnswerRequest;
import dev.bum.common.service.support.inquiry.enums.InquiryCategory;
import dev.bum.common.service.support.inquiry.enums.InquiryStatus;
import dev.bum.support_service.exception.InquiryStateConflictException;
import dev.bum.support_service.jpa.inquiry.Inquiry;
import dev.bum.support_service.jpa.inquiry.InquiryAnswer;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InquiryManagementServiceTest {

    @Mock
    private InquiryJpaRepository inquiryRepository;

    @Mock
    private InquiryAnswerJpaRepository answerRepository;

    private InquiryManagementService managementService;

    @BeforeEach
    void setUp() {
        managementService = new InquiryManagementService(inquiryRepository, answerRepository);
    }

    @Test
    void 관리자_답변을_등록하면_문의가_답변_완료된다() {
        Inquiry inquiry = Inquiry.create("user1", InquiryCategory.TICKET, "티켓 문의", "문의 내용");
        when(inquiryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(inquiry));
        when(answerRepository.findByInquiryId(1L)).thenReturn(Optional.empty());
        when(answerRepository.save(any(InquiryAnswer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = managementService.answer(
                1L,
                new InquiryAnswerRequest("  답변 내용입니다.  "),
                " admin1 "
        );

        assertThat(response.status()).isEqualTo(InquiryStatus.ANSWERED);
        assertThat(response.answer().responderId()).isEqualTo("admin1");
        assertThat(response.answer().content()).isEqualTo("답변 내용입니다.");
    }

    @Test
    void 이미_답변한_문의에는_답변을_추가할_수_없다() {
        Inquiry inquiry = Inquiry.create("user1", InquiryCategory.ETC, "문의", "문의 내용");
        InquiryAnswer answer = InquiryAnswer.create(inquiry, "admin1", "기존 답변");
        when(inquiryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(inquiry));
        when(answerRepository.findByInquiryId(1L)).thenReturn(Optional.of(answer));

        assertThatThrownBy(() -> managementService.answer(
                1L,
                new InquiryAnswerRequest("새 답변"),
                "admin2"
        ))
                .isInstanceOf(InquiryStateConflictException.class)
                .hasMessage("이미 답변이 등록된 문의입니다.");

        verify(answerRepository, never()).save(any());
    }
}
