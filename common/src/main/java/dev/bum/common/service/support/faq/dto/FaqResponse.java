package dev.bum.common.service.support.faq.dto;

import dev.bum.common.service.support.faq.enums.FaqCategory;
import dev.bum.common.service.support.notice.enums.PublicationStatus;

import java.time.LocalDateTime;

public record FaqResponse(
        Long faqId,
        String question,
        String answer,
        FaqCategory category,
        PublicationStatus status,
        int displayOrder,
        String authorId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
