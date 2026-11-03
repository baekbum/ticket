package dev.bum.common.service.support.faq.dto;

import dev.bum.common.service.support.faq.enums.FaqCategory;
import dev.bum.common.service.support.notice.enums.PublicationStatus;

import java.time.LocalDateTime;

public record FaqResponse(
        // 자주 묻는 질문 식별자.
        Long faqId,

        // 문의 내용.
        String question,

        // 문의 답변 내용.
        String answer,

        // 분류.
        FaqCategory category,

        // 자주 묻는 질문 상태.
        PublicationStatus status,

        // 화면 표시 순서.
        int displayOrder,

        // 작성자 ID.
        String authorId,

        // 생성 시각.
        LocalDateTime createdAt,

        // 마지막 수정 시각.
        LocalDateTime updatedAt
) {
}
