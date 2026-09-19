package dev.bum.support_service.mapper;

import dev.bum.common.service.support.faq.dto.FaqResponse;
import dev.bum.support_service.jpa.faq.Faq;

public final class FaqMapper {

    private FaqMapper() {
    }

    public static FaqResponse toResponse(Faq faq) {
        return new FaqResponse(
                faq.getId(), faq.getQuestion(), faq.getAnswer(), faq.getCategory(), faq.getStatus(),
                faq.getDisplayOrder(), faq.getAuthorId(), faq.getCreatedAt(), faq.getUpdatedAt()
        );
    }
}
