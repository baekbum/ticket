package dev.bum.support_service.mapper;

import dev.bum.common.service.support.inquiry.dto.InquiryAnswerResponse;
import dev.bum.common.service.support.inquiry.dto.InquiryResponse;
import dev.bum.common.service.support.inquiry.dto.InquirySummaryResponse;
import dev.bum.support_service.jpa.inquiry.Inquiry;
import dev.bum.support_service.jpa.inquiry.InquiryAnswer;

public final class InquiryMapper {

    private InquiryMapper() {
    }

    public static InquiryResponse toResponse(Inquiry inquiry, InquiryAnswer answer) {
        return new InquiryResponse(
                inquiry.getId(),
                inquiry.getRequesterId(),
                inquiry.getCategory(),
                inquiry.getTitle(),
                inquiry.getContent(),
                inquiry.getStatus(),
                answer == null ? null : toAnswerResponse(answer),
                inquiry.getCreatedAt(),
                inquiry.getUpdatedAt()
        );
    }

    public static InquirySummaryResponse toSummaryResponse(Inquiry inquiry) {
        return new InquirySummaryResponse(
                inquiry.getId(),
                inquiry.getRequesterId(),
                inquiry.getCategory(),
                inquiry.getTitle(),
                inquiry.getStatus(),
                inquiry.getCreatedAt(),
                inquiry.getUpdatedAt()
        );
    }

    private static InquiryAnswerResponse toAnswerResponse(InquiryAnswer answer) {
        return new InquiryAnswerResponse(
                answer.getId(),
                answer.getResponderId(),
                answer.getContent(),
                answer.getCreatedAt(),
                answer.getUpdatedAt()
        );
    }
}
