package dev.bum.common.service.support.inquiry.dto;

import dev.bum.common.service.support.inquiry.enums.InquiryCategory;
import dev.bum.common.service.support.inquiry.enums.InquiryStatus;

import java.time.LocalDateTime;

public record InquirySummaryResponse(
        // 문의 식별자.
        Long inquiryId,

        // 요청자 ID.
        String requesterId,

        // 분류.
        InquiryCategory category,

        // 제목.
        String title,

        // 문의 상태.
        InquiryStatus status,

        // 생성 시각.
        LocalDateTime createdAt,

        // 마지막 수정 시각.
        LocalDateTime updatedAt
) {
}
