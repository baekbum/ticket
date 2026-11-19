package dev.bum.common.service.support.inquiry.dto;

import java.time.LocalDateTime;

public record InquiryAnswerResponse(
        // 문의 답변 식별자.
        Long inquiryAnswerId,

        // 답변 작성자 ID.
        String responderId,

        // 본문.
        String content,

        // 생성 시각.
        LocalDateTime createdAt,

        // 마지막 수정 시각.
        LocalDateTime updatedAt
) {
}
