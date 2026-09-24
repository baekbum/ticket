package dev.bum.common.service.support.inquiry.dto;

import java.time.LocalDateTime;

public record InquiryAnswerResponse(
        Long inquiryAnswerId,
        String responderId,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
