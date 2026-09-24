package dev.bum.common.service.support.inquiry.dto;

import dev.bum.common.service.support.inquiry.enums.InquiryCategory;
import dev.bum.common.service.support.inquiry.enums.InquiryStatus;

import java.time.LocalDateTime;

public record InquirySummaryResponse(
        Long inquiryId,
        String requesterId,
        InquiryCategory category,
        String title,
        InquiryStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
