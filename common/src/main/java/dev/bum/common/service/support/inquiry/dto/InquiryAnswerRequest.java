package dev.bum.common.service.support.inquiry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InquiryAnswerRequest(
        @NotBlank(message = "답변 내용을 입력해주세요.")
        @Size(max = 10000, message = "답변 내용은 10,000자 이하여야 합니다.")
        String content
) {
}
