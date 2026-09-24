package dev.bum.common.service.support.inquiry.dto;

import dev.bum.common.service.support.inquiry.enums.InquiryCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateInquiryRequest(
        @NotNull(message = "문의 카테고리를 선택해주세요.")
        InquiryCategory category,
        @NotBlank(message = "문의 제목을 입력해주세요.")
        @Size(max = 200, message = "문의 제목은 200자 이하여야 합니다.")
        String title,
        @NotBlank(message = "문의 내용을 입력해주세요.")
        @Size(max = 10000, message = "문의 내용은 10,000자 이하여야 합니다.")
        String content
) {
}
