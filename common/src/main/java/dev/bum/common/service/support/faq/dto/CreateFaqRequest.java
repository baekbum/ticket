package dev.bum.common.service.support.faq.dto;

import dev.bum.common.service.support.faq.enums.FaqCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateFaqRequest(
        // 문의 내용.
        @NotBlank(message = "FAQ 질문을 입력해주세요.")
        @Size(max = 300, message = "FAQ 질문은 300자 이하여야 합니다.")
        String question,

        // 문의 답변 내용.
        @NotBlank(message = "FAQ 답변을 입력해주세요.")
        String answer,

        // 분류.
        @NotNull(message = "FAQ 카테고리를 선택해주세요.")
        FaqCategory category,

        // 화면 표시 순서.
        @Min(value = 0, message = "노출 순서는 0 이상이어야 합니다.")
        @Max(value = 9999, message = "노출 순서는 9999 이하여야 합니다.")
        int displayOrder,

        // 발행 여부.
        boolean published
) {
}
