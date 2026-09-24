package dev.bum.common.service.support.inquiry.dto;

import dev.bum.common.service.support.inquiry.enums.InquiryCategory;
import dev.bum.common.service.support.inquiry.enums.InquiryStatus;

import java.time.LocalDateTime;

public record InquiryResponse(
        Long inquiryId,
        String requesterId,
        InquiryCategory category,
        String title,
        String content,
        InquiryStatus status,
        InquiryAnswerResponse answer,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
